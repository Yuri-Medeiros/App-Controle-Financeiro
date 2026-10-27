package br.com.yurimedeiros.meufinanceiro

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import br.com.yurimedeiros.meufinanceiro.data.SecureDatabaseSession
import java.security.SecureRandom

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
            val first = SecureDatabaseSession.open(context, originalKey.copyOf(), name)
            val createdAt = first.createdAtMillis
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
            }
            result.putString("result", "PASS: encrypted Room persistence, wrong-key rejection, SQLCipher active")
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
