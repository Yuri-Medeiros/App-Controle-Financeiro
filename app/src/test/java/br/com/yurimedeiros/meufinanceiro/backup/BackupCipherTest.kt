package br.com.yurimedeiros.meufinanceiro.backup

import org.junit.Assert.*
import org.junit.Test

class BackupCipherTest {
    @Test fun recoveryWorksWithoutDeviceKeyAndUsesFreshRandomness() {
        val plain = "financial data with exact cents and Unicode: R$ 123,45 · ação".toByteArray()
        val password = "a recovery password independent of signing".toCharArray()
        val a = BackupCipher.encrypt(plain, password)
        val b = BackupCipher.encrypt(plain, password)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(plain, BackupCipher.decrypt(a, password))
        assertArrayEquals(plain, BackupCipher.decrypt(b, password))
        password.fill('\u0000')
    }
    @Test fun wrongPasswordAndTamperingAreRejected() {
        val password = "correct password for recovery".toCharArray()
        val encrypted = BackupCipher.encrypt("sensitive data".toByteArray(), password)
        assertTrue(runCatching { BackupCipher.decrypt(encrypted, "wrong password".toCharArray()) }.isFailure)
        val modified = encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertTrue(runCatching { BackupCipher.decrypt(modified, password) }.isFailure)
        val modifiedHeader = encrypted.copyOf().apply { this[12] = (this[12].toInt() xor 1).toByte() }
        assertTrue(runCatching { BackupCipher.decrypt(modifiedHeader, password) }.isFailure)
    }
    @Test fun unknownFormatsTruncationAndWeakNewPasswordsAreRejected() {
        assertTrue(runCatching { BackupCipher.decrypt(ByteArray(200), "password".toCharArray()) }.isFailure)
        assertTrue(runCatching { BackupCipher.decrypt(ByteArray(4), "password".toCharArray()) }.isFailure)
        assertTrue(runCatching { BackupCipher.encrypt(ByteArray(5), "short".toCharArray()) }.isFailure)
    }
}
