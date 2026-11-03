package br.com.yurimedeiros.meufinanceiro.gmail

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.util.Base64

class GmailInvestmentImportTest {
    @Test fun investmentRefreshReadsOnlyCustodyPdfEvenIfSearchReturnsOtherMessages() {
        val paths = mutableListOf<String>()
        fun message(subject: String, filename: String, sender: String = "todomundo@nubank.com.br") = JSONObject()
            .put("payload", JSONObject().put("headers", org.json.JSONArray()
                .put(JSONObject().put("name", "From").put("value", "Nubank <$sender>"))
                .put(JSONObject().put("name", "Subject").put("value", subject)))
                .put("filename", filename).put("body", JSONObject().put("size", 3)
                    .put("data", Base64.getUrlEncoder().encodeToString(byteArrayOf(1, 2, 3)))))
        val reader = GmailReader("test", request = { path ->
            paths += path
            when {
                path == "profile" -> JSONObject("{\"emailAddress\":\"test@example.com\"}")
                path.startsWith("messages?") -> JSONObject("{\"messages\":[{\"id\":\"custody\"},{\"id\":\"account\"},{\"id\":\"other\"},{\"id\":\"csv\"}]}")
                path.startsWith("messages/custody?") -> message("Seu extrato de custódia de investimentos chegou", "posicao.pdf")
                path.startsWith("messages/account?") -> message("Extrato da sua conta do Nubank", "conta.csv")
                path.startsWith("messages/other?") -> message("Seu extrato de custódia de investimentos chegou", "posicao.pdf", "other@example.com")
                else -> message("Seu extrato de custódia de investimentos chegou", "posicao.csv")
            }
        })
        val documents = mutableListOf<MailDocument>()
        reader.read(investmentsOnly = true, consume = documents::add)
        assertEquals(1, documents.size)
        assertTrue(documents.single().custody)
        assertArrayEquals(byteArrayOf(1, 2, 3), documents.single().bytes)
        val search = URLDecoder.decode(paths.first { it.startsWith("messages?") }, "UTF-8")
        assertTrue(search.contains("filename:pdf"))
        assertTrue(search.contains("subject:\"Seu extrato de custódia de investimentos chegou\""))
        assertFalse(search.contains("flashapp"))
    }

    @Test fun cancelledSessionDoesNotRequestGmail() {
        var called = false
        val reader = GmailReader("test", request = { called = true; JSONObject() }, shouldContinue = { false })
        assertTrue(runCatching { reader.read(investmentsOnly = true) {} }.isFailure)
        assertFalse(called)
    }
}
