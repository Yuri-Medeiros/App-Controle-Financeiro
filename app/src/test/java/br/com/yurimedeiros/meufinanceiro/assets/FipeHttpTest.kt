package br.com.yurimedeiros.meufinanceiro.assets

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI

class FipeHttpTest {
    private fun response(uri: URI, status: Int = 200, location: String? = null, body: String = "[]") = object : HttpURLConnection(uri.toURL()) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
    }
    @Test fun followsOnlyHttpsDestinationsOfDocumentedProvider() {
        val seen = mutableListOf<URI>()
        QuoteHttp.request("${FipeQuotes.BASE}/motorcycles/brands") { uri ->
            seen += uri
            if (seen.size == 1) response(uri, 302, "https://parallelum.com.br/fipe/api/v2/motorcycles/brands") else response(uri)
        }
        assertEquals(2, seen.size)
        for (target in listOf("https://evil.example/api", "http://fipe.parallelum.com.br/api/v2/references", "https://user:password@fipe.parallelum.com.br/api/v2/references")) {
            var opened = 0
            assertTrue(runCatching { QuoteHttp.request("${FipeQuotes.BASE}/references") { uri -> opened++; response(uri, 302, target) } }.isFailure)
            assertEquals(1, opened)
        }
    }
    @Test fun allowsCatalogLargerThanBitcoinPayloadButKeepsBoundedResponse() {
        val body = "{\"rows\":\"" + "x".repeat(100000) + "\"}"
        assertEquals(body, QuoteHttp.request("${FipeQuotes.BASE}/cars/brands/25/models") { response(it, body = body) }.body)
        assertTrue(runCatching { QuoteHttp.request("${FipeQuotes.BASE}/references") { response(it, body = "x".repeat(524289)) } }.isFailure)
    }
    @Test fun dailyQuotaFailureIsReportedWithoutParsingAnErrorAsPrice() {
        val error = runCatching { QuoteHttp.request("${FipeQuotes.BASE}/references") { response(it, 429) } }.exceptionOrNull()
        assertTrue(error is QuoteHttpException)
        assertTrue(FipeQuotes.failure(error as Exception).contains("amanhã"))
    }
}
