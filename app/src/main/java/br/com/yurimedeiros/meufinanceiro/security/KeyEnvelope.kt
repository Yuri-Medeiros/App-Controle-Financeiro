package br.com.yurimedeiros.meufinanceiro.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Versioned, bounded encoding; contains only IV and authenticated ciphertext. */
internal data class KeyEnvelope(val iv: ByteArray, val ciphertext: ByteArray) {
    fun encode(): ByteArray {
        require(iv.size == IV_SIZE && ciphertext.size == CIPHERTEXT_SIZE)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.write(iv)
                output.write(ciphertext)
            }
            bytes.toByteArray()
        }
    }

    companion object {
        private const val MAGIC = 0x4D464B31 // MFK1
        private const val IV_SIZE = 12
        private const val CIPHERTEXT_SIZE = 48 // 32-byte secret + 128-bit GCM tag
        const val ENCODED_SIZE = 4 + IV_SIZE + CIPHERTEXT_SIZE

        fun decode(bytes: ByteArray): KeyEnvelope {
            require(bytes.size == ENCODED_SIZE) { "Invalid key envelope length" }
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == MAGIC) { "Unsupported key envelope" }
                val iv = ByteArray(IV_SIZE).also(input::readFully)
                val ciphertext = ByteArray(CIPHERTEXT_SIZE).also(input::readFully)
                KeyEnvelope(iv, ciphertext)
            }
        }
    }
}
