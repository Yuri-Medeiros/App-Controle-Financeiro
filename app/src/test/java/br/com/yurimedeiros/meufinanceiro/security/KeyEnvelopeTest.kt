package br.com.yurimedeiros.meufinanceiro.security

import org.junit.Assert.*
import org.junit.Test

class KeyEnvelopeTest {
    private val envelope = KeyEnvelope(ByteArray(12) { it.toByte() }, ByteArray(48) { (it + 12).toByte() })

    @Test fun roundTripKeepsIvAndAuthenticatedCiphertext() {
        val restored = KeyEnvelope.decode(envelope.encode())
        assertArrayEquals(envelope.iv, restored.iv)
        assertArrayEquals(envelope.ciphertext, restored.ciphertext)
    }

    @Test fun truncationAndTrailingBytesAreRejected() {
        for (size in listOf(0, 4, 12, 63, 65, 10_000)) {
            assertThrows(IllegalArgumentException::class.java) { KeyEnvelope.decode(ByteArray(size)) }
        }
    }

    @Test fun unsupportedFormatIsNotSilentlyReplaced() {
        val bytes = envelope.encode()
        bytes[0] = 0
        assertThrows(IllegalArgumentException::class.java) { KeyEnvelope.decode(bytes) }
    }
}
