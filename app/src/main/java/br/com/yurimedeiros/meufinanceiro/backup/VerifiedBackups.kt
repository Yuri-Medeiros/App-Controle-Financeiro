package br.com.yurimedeiros.meufinanceiro.backup

import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset

internal interface BackupStore {
    fun upload(encrypted: ByteArray): DriveBackupFile
    fun read(file: DriveBackupFile): ByteArray
    fun list(): List<DriveBackupFile>
    fun delete(file: DriveBackupFile)
}
internal object VerifiedBackups {
    fun save(store: BackupStore, encrypted: ByteArray, protectedIds: Set<String> = emptySet(), today: LocalDate = LocalDate.now(ZoneOffset.UTC)): List<DriveBackupFile> {
        val created = store.upload(encrypted)
        require(created.size == encrypted.size.toLong()) { "Tamanho do novo backup não confere. Os anteriores foram preservados." }
        val returned = store.read(created)
        check(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(encrypted), MessageDigest.getInstance("SHA-256").digest(returned))) { "Falha na verificação do novo backup. Os anteriores foram preservados." }
        val all = store.list()
        check(all.any { it.id == created.id }) { "O novo backup ainda não foi confirmado na listagem. Os anteriores foram preservados." }
        val keep = BackupRetention.keep(all, today) + protectedIds + created.id
        all.filter { it.id !in keep }.forEach(store::delete)
        return store.list()
    }
}
