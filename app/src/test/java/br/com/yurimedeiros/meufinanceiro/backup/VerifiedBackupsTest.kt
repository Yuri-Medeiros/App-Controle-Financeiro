package br.com.yurimedeiros.meufinanceiro.backup

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class VerifiedBackupsTest {
    private class Store : BackupStore {
        val trace = mutableListOf<String>()
        val old = DriveBackupFile("old", Instant.parse("2026-01-01T00:00:00Z"), 3)
        val fresh = DriveBackupFile("new", Instant.parse("2026-10-30T12:00:00Z"), 3)
        var bytes = byteArrayOf(1, 2, 3)
        var failUpload = false; var failRead = false; var missingFromList = false
        val files = mutableListOf(old)
        override fun upload(encrypted: ByteArray): DriveBackupFile { trace += "upload"; check(!failUpload); files += fresh; return fresh }
        override fun read(file: DriveBackupFile): ByteArray { trace += "read"; check(!failRead); return bytes }
        override fun list(): List<DriveBackupFile> { trace += "list"; return files.filter { !missingFromList || it.id != fresh.id } }
        override fun delete(file: DriveBackupFile) { trace += "delete:${file.id}"; files.remove(file) }
    }
    private val today = LocalDate.of(2026, 10, 30)
    @Test fun corruptUploadCannotPrunePreviousBackups() {
        val s = Store().apply { bytes = byteArrayOf(3, 2, 1) }
        assertTrue(runCatching { VerifiedBackups.save(s, byteArrayOf(1, 2, 3), today = today) }.isFailure)
        assertTrue(s.trace.none { it.startsWith("delete") })
        assertTrue(s.old in s.files)
    }
    @Test fun failedUploadOrReadNeverDeletesOldFiles() {
        listOf(Store().apply { failUpload = true }, Store().apply { failRead = true }).forEach { s ->
            assertTrue(runCatching { VerifiedBackups.save(s, byteArrayOf(1, 2, 3), today = today) }.isFailure)
            assertTrue(s.trace.none { it.startsWith("delete") })
        }
    }
    @Test fun missingNewFileInListCannotPruneOldBackups() {
        val s = Store().apply { missingFromList = true }
        assertTrue(runCatching { VerifiedBackups.save(s, byteArrayOf(1, 2, 3), today = today) }.isFailure)
        assertTrue(s.trace.none { it.startsWith("delete") })
    }
    @Test fun cleanupOccursOnlyAfterReadbackAndListingConfirmTheNewCopy() {
        val s = Store()
        val result = VerifiedBackups.save(s, byteArrayOf(1, 2, 3), today = today)
        assertEquals(listOf("upload", "read", "list", "delete:old", "list"), s.trace)
        assertEquals(listOf(s.fresh), result)
    }
    @Test fun restoreSourceIsProtectedEvenOutsideTheRetentionWindow() {
        val s = Store()
        VerifiedBackups.save(s, byteArrayOf(1, 2, 3), setOf(s.old.id), today)
        assertTrue(s.old in s.files)
        assertTrue(s.trace.none { it.startsWith("delete") })
    }
}
