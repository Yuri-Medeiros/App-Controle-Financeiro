package br.com.yurimedeiros.meufinanceiro.assets

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI

class FipeHttpTest {
    private fun response(uri: URI, status: Int = 200, location: String? = null, body: String = "[]", age: String? = null) = object : HttpURLConnection(uri.toURL()) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?) = when (name) { "Location" -> location; "Age" -> age; else -> null }
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
    @Test fun fipeCatalogAcceptsLongLivedHttpCacheWhileBitcoinKeepsItsAgeLimit() {
        for (base in listOf(FipeQuotes.BASE, "https://parallelum.com.br/fipe/api/v1")) {
            for (age in listOf("172800", "31536000", "-1")) {
                val body = """[{"code":"80","name":"Honda"}]"""
                assertEquals(body, QuoteHttp.request("$base/motorcycles/brands") { response(it, body = body, age = age) }.body)
            }
        }
        val options = FipeQuotes.catalog("motorcycles/brands") { url ->
            QuoteHttp.request(url) { response(it, body = """[{"code":"80","name":"Honda"}]""", age = "172800") }.body
        }
        assertEquals(listOf(FipeOption("80", "Honda")), options)
        val failure = runCatching {
            QuoteHttp.request(BitcoinQuotes.COINBASE_URL) { response(it, age = "172800") }
        }.exceptionOrNull()
        assertTrue(failure is QuoteResponseException)
        assertTrue(failure?.message.orEmpty().contains("24 horas"))
    }

    @Test fun acceptingFipeHttpCacheDoesNotAcceptAnOutdatedPriceReference() {
        val path = "motos/marcas/80/modelos/1234/anos/2020-1"
        fun body(month: String) = """{"vehicleType":2,"price":"R$ 15.432,10","brand":"Honda","model":"Modelo","modelYear":2020,"fuelAcronym":"G","codeFipe":"811000-1","referenceMonth":"$month de 2026"}"""
        val today = java.time.LocalDate.of(2026, 11, 9)
        val quote = FipeQuotes.fetch(path, today) { url ->
            QuoteHttp.request(url) { response(it, body = body("novembro"), age = "172800") }.body
        }
        assertEquals(1543210L, quote.cents)
        for (month in listOf("outubro", "dezembro")) {
            var calls = 0
            val failure = runCatching { FipeQuotes.fetch(path, today) { url ->
                calls++
                QuoteHttp.request(url) { response(it, body = body(month), age = "172800") }.body
            } }.exceptionOrNull()
            assertTrue(failure is FipeValidationException)
            assertEquals(1, calls)
        }
    }

}
