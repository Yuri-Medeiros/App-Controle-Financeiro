package br.com.yurimedeiros.meufinanceiro.security

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Tests actual AES-GCM and the ownership boundary; Keystore UI needs Android. */
class UnlockOperationTest {
    private val aad = "br.com.yurimedeiros.meufinanceiro/database-key/v1".toByteArray()
    private val wrappingKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun initializationWrapsTheKeyAndWipesTheTransientCopy() {
        val password = ByteArray(32).also(SecureRandom()::nextBytes)
        val expected = password.copyOf()
        val encryption = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }
        var saved: KeyEnvelope? = null
        val operation = UnlockOperation(encryption, password, null, aad) { saved = it }
        val result = operation.finish(encryption)
        assertArrayEquals(expected, result)
        assertArrayEquals(ByteArray(32), password)
        assertFalse(saved!!.ciphertext.contentEquals(expected))
        val decryption = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(128, saved!!.iv))
        }
        assertArrayEquals(expected, UnlockOperation(decryption, null, saved, aad) { fail() }.finish(decryption))
        result.fill(0)
        expected.fill(0)
    }

    @Test fun canceledOperationCannotBeUsedAndWipesFreshSecret() {
        val secret = ByteArray(32) { 7 }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }
        val operation = UnlockOperation(cipher, secret, null, aad) { fail() }
        operation.close()
        assertArrayEquals(ByteArray(32), secret)
        assertThrows(IllegalStateException::class.java) { operation.finish(cipher) }
    }

    @Test fun replacingTheAuthenticatedCipherIsRejected() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }
        val replacement = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }
        val operation = UnlockOperation(cipher, ByteArray(32), null, aad) { fail() }
        assertThrows(IllegalStateException::class.java) { operation.finish(replacement) }
        operation.close()
    }

    @Test fun changingCiphertextDoesNotYieldADatabasePassword() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(ByteArray(32))
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        val envelope = KeyEnvelope(cipher.iv, ciphertext)
        val decryption = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(128, envelope.iv))
        }
        assertThrows(AEADBadTagException::class.java) {
            UnlockOperation(decryption, null, envelope, aad) { fail() }.finish(decryption)
        }
    }

    @Test fun failedEnvelopeWriteDoesNotReturnOrRetainPlaintextKey() {
        val secret = ByteArray(32) { 9 }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }
        val operation = UnlockOperation(cipher, secret, null, aad) { error("storage unavailable") }
        val failure = assertThrows(IllegalStateException::class.java) { operation.finish(cipher) }
        assertEquals("U4-OTHER", operation.failureCode(failure))
        assertFalse(operation.failureCode(failure).contains("storage unavailable"))
        assertArrayEquals(ByteArray(32), secret)
        assertThrows(IllegalStateException::class.java) { operation.finish(cipher) }
    }
}
