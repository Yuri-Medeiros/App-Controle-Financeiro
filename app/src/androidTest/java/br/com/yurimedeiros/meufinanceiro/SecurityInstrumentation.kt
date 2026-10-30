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
import br.com.yurimedeiros.meufinanceiro.backup.*
import br.com.yurimedeiros.meufinanceiro.ledger.*
import java.time.LocalDate
import br.com.yurimedeiros.meufinanceiro.people.PeopleRepository

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
            val people = PeopleRepository(first.database)
            check(people.all().size == 11 && people.all().none { it.name == "Eu" })
            people.add("Pessoa de teste")
            check(runCatching { people.add("  PESSOA   DE TESTE ") }.isFailure)
            check(people.all().size == 12)
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
            val conflict = repo.all().single { it.status == "CONFLICT" }
            val failedBatch = runCatching { repo.confirmBatch(linkedMapOf(item.id to ("Transporte" to ""), conflict.id to ("Transporte" to ""))) }
            check(failedBatch.isFailure)
            check(repo.all().single { it.id == item.id }.category == "Alimentação")
            val before = repo.all().size
            runCatching { repo.import(source("invalid", csv + "linha inválida")) }
            check(repo.all().size == before)
            check(first.database.movements().document("example@test.invalid:invalid") == null)
            val personId = people.all().single { it.name == "Pessoa de teste" }.id
            val card = MailDocument("example@test.invalid:card", "Nubank_2026-11-08.csv", DocumentKind.NUBANK_CARD,
                "date,title,amount\n2026-11-01,Loja - 1/2,\"200,00\"\n2026-11-02,Estorno da loja,\"-40,00\"\n".toByteArray())
            repo.import(card, LocalDate.of(2026, 11, 8))
            val cardRow = repo.all().single { it.row.kind == DocumentKind.NUBANK_CARD && it.row.cents > 0 }
            val credit = repo.all().single { it.row.kind == DocumentKind.NUBANK_CARD && it.row.cents < 0 }
            repo.import(source("transfer", "Data,Valor,Identificador,Descrição\n03/11/2026,100.00,00000000-0000-4000-8000-000000000002,Transferência recebida - Pessoa de teste\n"))
            val transfer = repo.all().single { it.row.cents == 10000L }
            val ledger = LedgerRepository(first.database)
            val due = LocalDate.of(2026, 11, 9)
            val plan = PurchasePlan("test-plan", "Loja", "Casa", listOf(SchedulePart(1, due, 20000, cardRow.id), SchedulePart(2, due.plusMonths(1), 20000)), linkedMapOf(personId to listOf(10000L, 10000L), 0L to listOf(10000L, 10000L)), familyStart = LocalDate.of(2026, 11, 1))
            ledger.append(LedgerAction.Purchase(plan))
            check(ledger.snapshot().purchases.single().shares.keys.toList() == listOf(personId, 0L))
            val historySize = first.database.financialEvents().all().size
            check(runCatching { ledger.append(LedgerAction.Payment(personId, DebtDirection.RECEIVE, transfer.id, transfer.row.date, mapOf("test-plan:1:$personId" to 9999))) }.isFailure)
            check(first.database.financialEvents().all().size == historySize)
            ledger.append(LedgerAction.Payment(personId, DebtDirection.RECEIVE, transfer.id, transfer.row.date, mapOf("test-plan:1:$personId" to 10000)))
            ledger.append(LedgerAction.Refund(plan.id, credit.id, credit.row.date, mapOf("test-plan:1:$personId" to 4000)))
            check(ledger.snapshot().debts.single { it.direction == DebtDirection.PAY }.remaining == 4000L)
            check(ledger.snapshot().debts.single { it.number == 2 }.remaining == 10000L)
            first.database.openHelper.writableDatabase.query("PRAGMA cipher_version").use {
                check(it.moveToFirst() && it.getString(0).isNotBlank())
            }
            val recoveryData = BackupPayload.export(first.database)
            check(BackupPayload.preview(recoveryData).movements == 5)
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
                val savedPeople = PeopleRepository(it.database).all()
                check(savedPeople.size == 12 && savedPeople.any { person -> person.name == "Pessoa de teste" })
                val saved = MovementRepository(it.database).all()
                check(saved.size == 5 && saved.any { row -> row.category == "Alimentação" && row.status == "CONFIRMED" })
                val ledgerSaved = LedgerRepository(it.database).snapshot()
                check(ledgerSaved.actions.size == 3)
                check(ledgerSaved.debts.single { debt -> debt.direction == DebtDirection.PAY }.remaining == 4000L)
            }
            val freshName = "$name-fresh"
            try {
                context.deleteDatabase(freshName)
                SecureDatabaseSession.open(context, originalKey.copyOf(), freshName).use {
                    check(PeopleRepository(it.database).all().size == 11)
                    BackupPayload.restore(it.database, recoveryData)
                    check(MovementRepository(it.database).all().size == 5)
                    check(PeopleRepository(it.database).all().size == 12)
                    check(LedgerRepository(it.database).snapshot().debts.single { d -> d.direction == DebtDirection.PAY }.remaining == 4000L)
                    val invalidBackup = org.json.JSONObject(recoveryData.toString(Charsets.UTF_8)).put("version", 999).toString().toByteArray()
                    check(runCatching { BackupPayload.restore(it.database, invalidBackup) }.isFailure)
                    check(MovementRepository(it.database).all().size == 5)
                }
            } finally { context.deleteDatabase(freshName) }
            recoveryData.fill(0)
            result.putString("result", "PASS: backup restore and malformed backup rejection; financial history and shares order; people seed, uniqueness and persistence; encrypted schema 1 migration, imports, deduplication, atomic rejection, review persistence, wrong-key rejection")
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
