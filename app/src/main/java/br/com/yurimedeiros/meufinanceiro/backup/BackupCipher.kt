package br.com.yurimedeiros.meufinanceiro.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable recovery envelope. Never includes the device's database/Keystore key. */
internal object BackupCipher {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val magic = "MFBK0001".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 600_000
    private const val HEADER_SIZE = 8 + 4 + 16 + 12
    private fun key(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 12) { "Use pelo menos 12 caracteres na senha de recuperação." }
        require(plain.size <= MAX_BYTES)
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE).put(magic).putInt(ITERATIONS).put(salt).put(nonce).array()
        val key = key(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(plain)
        } finally { key.fill(0) }
    }
    fun decrypt(envelope: ByteArray, password: CharArray): ByteArray {
        require(envelope.size in (HEADER_SIZE + 16)..(MAX_BYTES + HEADER_SIZE + 16))
        val buffer = ByteBuffer.wrap(envelope)
        val signature = ByteArray(8).also(buffer::get)
        require(signature.contentEquals(magic) && buffer.int == ITERATIONS) { "Formato de backup não suportado." }
        val salt = ByteArray(16).also(buffer::get); val nonce = ByteArray(12).also(buffer::get)
        val key = key(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(envelope.copyOfRange(0, HEADER_SIZE))
            cipher.doFinal(envelope, HEADER_SIZE, envelope.size - HEADER_SIZE)
        } finally { key.fill(0) }
    }
}
