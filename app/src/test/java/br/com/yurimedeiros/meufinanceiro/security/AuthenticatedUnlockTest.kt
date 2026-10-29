package br.com.yurimedeiros.meufinanceiro.security

import java.security.AlgorithmParameters
import java.security.Key
import java.security.Provider
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.*
import org.junit.Test

/** Models Android's cached pre-authentication AAD error while using real AES-GCM. */
class AuthenticatedUnlockTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val aad = "br.com.yurimedeiros.meufinanceiro/database-key/v1".toByteArray()

    @Test fun freshUnlockDoesNotTouchKeyUntilAuthorizedAndProducesCompatibleEnvelope() {
        val gate = AuthGateSpi()
        val cipher = AuthGateCipher(gate)
        var saved: KeyEnvelope? = null
        val operation = UnlockOperation.prepare(cipher, key, null, aad) { saved = it }
        assertEquals(0, gate.updates)
        gate.authorized = true // BiometricPrompt success, for either supported authenticator.
        val password = operation.finish(cipher)
        assertEquals(32, password.size)
        assertEquals(1, gate.updates)
        // Existing on-disk format/AAD can be read by an independent AES-GCM cipher.
        val reader = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, saved!!.iv))
            updateAAD(aad)
        }
        assertArrayEquals(password, reader.doFinal(saved!!.ciphertext))
    }

    @Test fun existingEnvelopeUnlockWaitsForAuthenticationAndNeverRewritesIt() {
        val expected = ByteArray(32).also(SecureRandom()::nextBytes)
        val writer = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(aad)
        }
        val envelope = KeyEnvelope(writer.iv, writer.doFinal(expected))
        val gate = AuthGateSpi()
        val reader = AuthGateCipher(gate)
        val operation = UnlockOperation.prepare(reader, key, envelope, aad) { fail("Existing envelope must be preserved") }
        assertEquals(0, gate.updates)
        gate.authorized = true
        assertArrayEquals(expected, operation.finish(reader))
    }

    @Test fun oldPreAuthenticationUpdateStillFailsAfterSuccessfulPrompt() {
        val gate = AuthGateSpi()
        val cipher = AuthGateCipher(gate)
        val operation = UnlockOperation.prepare(cipher, key, null, aad) { fail() }
        cipher.updateAAD(aad) // Old prepareUnlock: failure is cached, not thrown here.
        gate.authorized = true
        assertThrows(IllegalBlockSizeException::class.java) { operation.finish(cipher) }
    }

    private class AuthGateCipher(spi: AuthGateSpi) : Cipher(spi,
        object : Provider("AuthenticationGate", 1.0, "Test-only authorization gate") {}, "AES/GCM/NoPadding")

    private class AuthGateSpi : CipherSpi() {
        private val delegate = Cipher.getInstance("AES/GCM/NoPadding")
        var authorized = false
        var updates = 0
        private var cachedFailure = false
        override fun engineUpdateAAD(input: ByteArray, offset: Int, length: Int) {
            updates++
            if (!authorized) { cachedFailure = true; return }
            if (!cachedFailure) delegate.updateAAD(input, offset, length)
        }
        private fun checkAuthorization() {
            if (cachedFailure || !authorized) throw IllegalBlockSizeException("Key operation was not authorized")
        }
        override fun engineDoFinal(input: ByteArray, offset: Int, length: Int): ByteArray {
            checkAuthorization()
            return delegate.doFinal(input, offset, length)
        }
        override fun engineDoFinal(input: ByteArray, offset: Int, length: Int, output: ByteArray, outputOffset: Int): Int {
            checkAuthorization()
            return delegate.doFinal(input, offset, length, output, outputOffset)
        }
        override fun engineUpdate(input: ByteArray, offset: Int, length: Int): ByteArray { checkAuthorization(); return delegate.update(input, offset, length) }
        override fun engineUpdate(input: ByteArray, offset: Int, length: Int, output: ByteArray, outputOffset: Int): Int {
            checkAuthorization(); return delegate.update(input, offset, length, output, outputOffset)
        }
        override fun engineInit(mode: Int, key: Key, random: SecureRandom?) = delegate.init(mode, key, random)
        override fun engineInit(mode: Int, key: Key, params: AlgorithmParameterSpec?, random: SecureRandom?) = delegate.init(mode, key, params, random)
        override fun engineInit(mode: Int, key: Key, params: AlgorithmParameters?, random: SecureRandom?) = delegate.init(mode, key, params, random)
        override fun engineGetBlockSize() = delegate.blockSize
        override fun engineGetOutputSize(size: Int) = delegate.getOutputSize(size)
        override fun engineGetIV(): ByteArray = delegate.iv
        override fun engineGetParameters(): AlgorithmParameters = delegate.parameters
        override fun engineSetMode(mode: String) { require(mode == "GCM") }
        override fun engineSetPadding(padding: String) { require(padding == "NoPadding") }
    }
}
