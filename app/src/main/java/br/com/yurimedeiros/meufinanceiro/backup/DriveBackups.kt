package br.com.yurimedeiros.meufinanceiro.backup

import br.com.yurimedeiros.meufinanceiro.BuildConfig
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal data class DriveBackupFile(val id: String, val created: Instant, val size: Long)

internal object BackupRetention {
    fun keep(files: List<DriveBackupFile>, today: LocalDate): Set<String> {
        val sorted = files.sortedByDescending { it.created }
        val daily = sorted.filter { it.created.atOffset(ZoneOffset.UTC).toLocalDate() >= today.minusDays(14) }
            .distinctBy { it.created.atOffset(ZoneOffset.UTC).toLocalDate() }.map { it.id }
        return (daily + listOfNotNull(sorted.firstOrNull()?.id)).toSet()
    }
}

/** Only app-owned files in drive.appdata. Upload, read-back verification, then retention. */
internal class DriveBackups(private val token: String, private val active: () -> Boolean) {
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun request(path: String, method: String = "GET", body: ByteArray? = null, type: String = "application/json", upload: Boolean = false): ByteArray {
        check(active()) { "Sessão encerrada." }
        val root = if (upload) "https://www.googleapis.com/upload/drive/v3/" else "https://www.googleapis.com/drive/v3/"
        val c = URI(root + path).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 20000; c.readTimeout = 30000; c.instanceFollowRedirects = false
            c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", type); c.setFixedLengthStreamingMode(body.size); c.outputStream.use { it.write(body) } }
            val code = c.responseCode
            check(code in 200..299) { if (code == 401 || code == 403) "Autorize o Drive novamente para continuar o backup." else "Drive indisponível (HTTP $code). Seu backup anterior foi preservado." }
            val bytes = c.inputStream.use { it.readNBytes(BackupCipher.MAX_BYTES + 1024) }
            require(bytes.size < BackupCipher.MAX_BYTES + 1024) { "Backup acima do limite de tamanho." }
            check(active()) { "Sessão encerrada." }
            return bytes
        } finally { c.disconnect() }
    }
    fun list(): List<DriveBackupFile> {
        val files = mutableListOf<DriveBackupFile>(); var page = ""
        do {
            val query = "trashed = false and appProperties has { key='format' and value='meu-financeiro-v1' } and appProperties has { key='applicationId' and value='${BuildConfig.APPLICATION_ID}' }"
            val json = JSONObject(request("files?spaces=appDataFolder&q=${encode(query)}&fields=nextPageToken,files(id,createdTime,size)&pageSize=1000&pageToken=${encode(page)}").toString(Charsets.UTF_8))
            val list = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until list.length()) list.getJSONObject(i).let { files += DriveBackupFile(it.getString("id"), Instant.parse(it.getString("createdTime")), it.optLong("size")) }
            page = json.optString("nextPageToken")
            require(files.size <= 10000)
        } while (page.isNotEmpty())
        return files.sortedByDescending { it.created }
    }
    fun read(file: DriveBackupFile): ByteArray {
        require(file.size in 1..(BackupCipher.MAX_BYTES + 128L))
        return request("files/${encode(file.id)}?alt=media")
    }
    private fun upload(encrypted: ByteArray): DriveBackupFile {
        val boundary = "finance-${UUID.randomUUID()}"
        val metadata = JSONObject().put("name", "Meu-Financeiro-${Instant.now()}.mfb")
            .put("parents", JSONArray(listOf("appDataFolder"))).put("appProperties", JSONObject().put("format", "meu-financeiro-v1").put("applicationId", BuildConfig.APPLICATION_ID))
        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray()
        val body = head + encrypted + "\r\n--$boundary--\r\n".toByteArray()
        val uploaded = JSONObject(request("files?uploadType=multipart&fields=id,size,createdTime", "POST", body, "multipart/related; boundary=$boundary", upload = true).toString(Charsets.UTF_8))
        val file = DriveBackupFile(uploaded.getString("id"), Instant.parse(uploaded.getString("createdTime")), uploaded.getLong("size"))
        return file
    }
    fun save(encrypted: ByteArray, protectedIds: Set<String> = emptySet()): List<DriveBackupFile> = VerifiedBackups.save(object : BackupStore {
        override fun upload(encrypted: ByteArray) = this@DriveBackups.upload(encrypted)
        override fun read(file: DriveBackupFile) = this@DriveBackups.read(file)
        override fun list() = this@DriveBackups.list()
        override fun delete(file: DriveBackupFile) { request("files/${encode(file.id)}", "DELETE") }
    }, encrypted, protectedIds)

}
