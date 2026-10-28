package br.com.yurimedeiros.meufinanceiro.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import br.com.yurimedeiros.meufinanceiro.data.SecureDatabaseSession
import java.io.Closeable
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class KeyVault(private val context: Context) {
    private val envelopeFile = AtomicFile(File(context.noBackupFilesDir, "database-key.v1"))
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** No key use is allowed until this cipher is authorized by BiometricPrompt. */
    fun prepareUnlock(): UnlockOperation {
        val existing = if (envelopeFile.baseFile.exists() || File(envelopeFile.baseFile.path + ".bak").exists()) {
            val bytes = envelopeFile.openRead().use { it.readNBytes(KeyEnvelope.ENCODED_SIZE + 1) }
            KeyEnvelope.decode(bytes)
        } else null
        val databaseExists = context.getDatabasePath(SecureDatabaseSession.DATABASE_NAME).exists()
        check(existing != null || !databaseExists) { "Database exists without its key envelope" }

        val key = if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } else {
            check(existing == null) { "Wrapping key unavailable; preserve database" }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationParameters(
                            0,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                        )
                        .setInvalidatedByBiometricEnrollment(false)
                        .build(),
                )
                generateKey()
            }
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return UnlockOperation.prepare(cipher, key, existing, AAD) { envelope ->
            val output = envelopeFile.startWrite()
            try {
                output.write(envelope.encode())
                envelopeFile.finishWrite(output)
            } catch (error: Throwable) {
                envelopeFile.failWrite(output)
                throw error
            }
        }
    }

    companion object {
        private const val KEY_ALIAS = "meu-financeiro.database-wrap.v1"
        private val AAD = "br.com.yurimedeiros.meufinanceiro/database-key/v1".toByteArray(Charsets.UTF_8)
    }
}

internal class UnlockOperation(
    val cipher: Cipher,
    private var freshPassword: ByteArray?,
    private val existing: KeyEnvelope?,
    private val additionalAuthenticatedData: ByteArray,
    private val persist: (KeyEnvelope) -> Unit,
) : Closeable {
    private var completed = false
    private var stage = "U1"

    /** Fixed identifiers only: never expose provider messages, paths or key material. */
    fun failureCode(error: Exception): String {
        val names = generateSequence<Throwable>(error) { it.cause }.take(8).map { it.javaClass.simpleName }.toSet()
        val reason = when {
            "UserNotAuthenticatedException" in names -> "AUTH"
            "AEADBadTagException" in names -> "TAG"
            "KeyPermanentlyInvalidatedException" in names -> "KEY"
            "IOException" in names -> "IO"
            else -> "OTHER"
        }
        return "$stage-$reason"
    }

    /** Use only the exact cipher authorized by the system callback. */
    fun finish(authorizedCipher: Cipher): ByteArray {
        check(!completed && authorizedCipher === cipher)
        completed = true
        try {
            // Keystore updateAAD is a key operation too. Before BiometricPrompt succeeds,
            // Android may cache a UserNotAuthenticated failure until doFinal, even when
            // the prompt subsequently succeeds. Keep preparation free of key updates.
            stage = "U2"
            authorizedCipher.updateAAD(additionalAuthenticatedData)
            stage = "U3"
            if (existing != null) return authorizedCipher.doFinal(existing.ciphertext).also {
                check(it.size == 32)
            }
            val password = checkNotNull(freshPassword)
            val ciphertext = authorizedCipher.doFinal(password)
            val envelope = KeyEnvelope(authorizedCipher.iv, ciphertext)
            stage = "U4"
            persist(envelope)
            return password.copyOf()
        } finally {
            freshPassword?.fill(0)
            freshPassword = null
        }
    }

    companion object {
        /** Prepare an operation handle, without sending AAD or plaintext before authorization. */
        fun prepare(
            cipher: Cipher,
            key: SecretKey,
            existing: KeyEnvelope?,
            additionalAuthenticatedData: ByteArray,
            persist: (KeyEnvelope) -> Unit,
        ): UnlockOperation {
            if (existing != null) {
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, existing.iv))
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, key)
            }
            val freshPassword = if (existing == null) ByteArray(32).also(SecureRandom()::nextBytes) else null
            return UnlockOperation(cipher, freshPassword, existing, additionalAuthenticatedData, persist)
        }
    }

    override fun close() {
        completed = true
        freshPassword?.fill(0)
        freshPassword = null
    }
}
