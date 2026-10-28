package br.com.yurimedeiros.meufinanceiro

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import br.com.yurimedeiros.meufinanceiro.data.SecureDatabaseSession
import java.security.SecureRandom
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import br.com.yurimedeiros.meufinanceiro.gmail.MailDocument
import br.com.yurimedeiros.meufinanceiro.importing.DocumentKind
import br.com.yurimedeiros.meufinanceiro.importing.MovementRepository

/** Integration check using platform Instrumentation, without a test service. */
class SecurityInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val name = "instrumentation-encryption-check.db"
        val context = targetContext
        val originalKey = ByteArray(32).also(SecureRandom()::nextBytes)
        var resultCode = Activity.RESULT_CANCELED
        try {
            context.deleteDatabase(name)
            System.loadLibrary("sqlcipher")
            // Create the real encrypted schema 1, then let Room validate and migrate it.
            val helper = SupportOpenHelperFactory(originalKey.copyOf()).create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE local_state (id INTEGER NOT NULL PRIMARY KEY, createdAtMillis INTEGER NOT NULL)")
                            db.execSQL("INSERT INTO local_state VALUES (1, 123456)")
                            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                            db.execSQL("INSERT INTO room_master_table VALUES (42, '20debd408eaf2559cfd93232acf2c6e2')")
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
                    }).build()
            )
            helper.writableDatabase
            helper.close()
            val first = SecureDatabaseSession.open(context, originalKey.copyOf(), name)
            val createdAt = first.createdAtMillis
            check(createdAt == 123456L)
            val repo = MovementRepository(first.database)
            val csv = "Data,Valor,Identificador,Descrição\n01/09/2026,-10.00,00000000-0000-4000-8000-000000000001,Compra no débito - Loja\n"
            fun source(key: String, text: String = csv) = MailDocument("example@test.invalid:$key", "sample.csv", DocumentKind.NUBANK_ACCOUNT, text.toByteArray())
            repo.import(source("one"))
            repo.import(source("one"))
            repo.import(source("two"))
            check(repo.all().size == 1)
            val item = repo.all().single()
            repo.confirm(setOf(item.id), "Alimentação", "Loja de teste")
            check(repo.all().single().category == "Alimentação")
            repo.import(source("conflict", csv.replace("-10.00", "-9.00")))
            check(repo.all().count { it.status == "CONFLICT" } == 1)
            val before = repo.all().size
            runCatching { repo.import(source("invalid", csv + "linha inválida")) }
            check(repo.all().size == before)
            check(first.database.movements().document("example@test.invalid:invalid") == null)
            first.database.openHelper.writableDatabase.query("PRAGMA cipher_version").use {
                check(it.moveToFirst() && it.getString(0).isNotBlank())
            }
            first.close()
            val header = context.getDatabasePath(name).inputStream().use { it.readNBytes(16) }
            check(!header.contentEquals("SQLite format 3\u0000".toByteArray()))

            var wrongKeyRejected = false
            try {
                SecureDatabaseSession.open(context, ByteArray(32).also(SecureRandom()::nextBytes), name).use { }
            } catch (_: Exception) { wrongKeyRejected = true }
            check(wrongKeyRejected)
            SecureDatabaseSession.open(context, originalKey.copyOf(), name).use {
                check(it.createdAtMillis == createdAt)
                val saved = MovementRepository(it.database).all()
                check(saved.size == 2 && saved.any { row -> row.category == "Alimentação" && row.status == "CONFIRMED" })
            }
            result.putString("result", "PASS: encrypted schema 1 migration, imports, deduplication, atomic rejection, review persistence, wrong-key rejection")
            resultCode = Activity.RESULT_OK
        } catch (_: Exception) {
            result.putString("result", "FAIL: encrypted storage integration check")
        } finally {
            originalKey.fill(0)
            context.deleteDatabase(name)
        }
        finish(resultCode, result)
    }
}
