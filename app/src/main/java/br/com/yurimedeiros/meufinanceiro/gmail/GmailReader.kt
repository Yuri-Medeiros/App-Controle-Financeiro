package br.com.yurimedeiros.meufinanceiro.gmail

import android.util.Base64
import br.com.yurimedeiros.meufinanceiro.importing.DocumentKind
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import org.json.JSONObject

internal data class MailDocument(val source: String, val filename: String, val kind: DocumentKind, val bytes: ByteArray)

/** Token and attachments exist only in memory; no message bodies or credentials are logged. */
internal class GmailReader(private val token: String) {
    fun read(consume: (MailDocument) -> Unit): Int {
        val account = get("profile").getString("emailAddress")
        var page = ""
        var skipped = 0
        do {
            val query = "has:attachment {from:todomundo@nubank.com.br from:noreply@flashapp.com.br}"
            val list = get("messages?maxResults=100&q=${encode(query)}&pageToken=${encode(page)}")
            val messages = list.optJSONArray("messages")
            for (i in 0 until (messages?.length() ?: 0)) {
                val id = messages!!.getJSONObject(i).getString("id")
                val message = get("messages/$id?format=full")
                val payload = message.getJSONObject("payload")
                val headers = payload.optJSONArray("headers")
                fun header(name: String): String = (0 until (headers?.length() ?: 0)).map { headers!!.getJSONObject(it) }
                    .firstOrNull { it.optString("name").equals(name, true) }?.optString("value").orEmpty()
                val from = header("From").trim().let { Regex("<([^<>]+)>").find(it)?.groupValues?.get(1) ?: it }.lowercase()
                val subject = header("Subject").trim()
                val kind = when {
                    from == "noreply@flashapp.com.br" && subject == "Seu extrato Flash chegou!" -> DocumentKind.FLASH
                    from == "todomundo@nubank.com.br" && subject == "Extrato da sua conta do Nubank" -> DocumentKind.NUBANK_ACCOUNT
                    from == "todomundo@nubank.com.br" && subject == "Extrato da fatura do Cartão Nubank" -> DocumentKind.NUBANK_CARD
                    else -> null
                }
                if (kind == null) { skipped++; continue }
                fun visit(part: JSONObject, path: String) {
                    val filename = part.optString("filename")
                    if (filename.isNotEmpty()) {
                        if (!filename.endsWith(".csv", true)) { skipped++; return }
                        val body = part.getJSONObject("body")
                        require(body.optLong("size") <= 10 * 1024 * 1024) { "Anexo acima do limite de 10 MB." }
                        val attachment = body.optString("attachmentId")
                        val encoded = if (attachment.isNotEmpty()) get("messages/$id/attachments/${encode(attachment)}").getString("data") else body.optString("data")
                        consume(MailDocument("$account:$id:$path", filename, kind, Base64.decode(encoded, Base64.URL_SAFE)))
                    }
                    val parts = part.optJSONArray("parts")
                    for (j in 0 until (parts?.length() ?: 0)) visit(parts!!.getJSONObject(j), "$path.$j")
                }
                visit(payload, "0")
            }
            page = list.optString("nextPageToken")
        } while (page.isNotEmpty())
        return skipped
    }

    private fun get(path: String): JSONObject {
        val connection = URI("https://gmail.googleapis.com/gmail/v1/users/me/$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            val code = connection.responseCode
            check(code in 200..299) {
                if (code == 401 || code == 403) "Acesso ao Gmail indisponível. Toque em Atualizar para autorizar novamente."
                else "Não foi possível consultar o Gmail (HTTP $code). Tente novamente."
            }
            // Bound the response before allocation; base64 for a 10 MB attachment fits this limit.
            val bytes = connection.inputStream.use { it.readNBytes(15 * 1024 * 1024 + 1) }
            require(bytes.size <= 15 * 1024 * 1024) { "Resposta do Gmail acima do limite." }
            return JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
