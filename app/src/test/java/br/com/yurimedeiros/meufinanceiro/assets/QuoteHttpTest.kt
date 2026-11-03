package br.com.yurimedeiros.meufinanceiro.assets

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

class QuoteHttpTest {
    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val json = """[{"pair":"BTC-BRL","last":"500000.25","date":${now.epochSecond}}]"""
    private class Response(url: URI, private val code: Int, private val body: String = "", private val headers: Map<String,String> = emptyMap()) : HttpURLConnection(url.toURL()) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getHeaderField(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
    }
    private fun ok(uri: URI, body: String = json) = Response(uri, 200, body, mapOf("Content-Type" to "application/json", "Date" to "Tue, 03 Nov 2026 12:00:00 GMT"))

    @Test fun follows302RelativeLocationAndCalculatesQuoteThroughProductionTransport() {
        val calls = mutableListOf<Response>()
        val quote = BitcoinQuotes.fetch { url -> QuoteHttp.request(url) { uri ->
            val response = if (calls.isEmpty()) Response(uri, 302, headers = mapOf("Location" to "/api/v4/tickers/?symbols=BTC-BRL")) else ok(uri)
            calls += response; response
        } }
        assertEquals("500000.25", quote.price)
        assertEquals(2, calls.size)
        assertTrue(calls.all { it.closed })
        assertTrue(calls.all { !it.instanceFollowRedirects && it.getRequestProperty("Accept") == "application/json" })
        assertEquals("/api/v4/tickers/", calls.last().url.path)
    }

    @Test fun acceptsOfficialHttpsHostRedirect() {
        val calls = mutableListOf<URI>()
        val response = QuoteHttp.request(BitcoinQuotes.MERCADO_URL) { uri ->
            calls += uri
            if (calls.size == 1) Response(uri, 302, headers = mapOf("Location" to "https://www.mercadobitcoin.net/api/BTC/ticker/")) else ok(uri)
        }
        assertEquals(json, response.body)
        assertEquals("www.mercadobitcoin.net", calls.last().host)
    }

    @Test fun rejectsHttpExternalHostsLoopsAndMissingLocations() {
        for (location in listOf("http://api.mercadobitcoin.net/test", "https://attacker.example/test", "https://api.mercadobitcoin.net.attacker.example/", BitcoinQuotes.MERCADO_URL, "")) {
            var opened = 0
            val result = runCatching { QuoteHttp.request(BitcoinQuotes.MERCADO_URL) { uri ->
                opened++; Response(uri, 302, headers = mapOf("Location" to location))
            } }
            assertTrue(location, result.isFailure)
            assertEquals(1, opened)
        }
    }

    @Test fun boundsRedirectChainAndRejectsHtmlAndExpiredResponses() {
        var opened = 0
        assertTrue(runCatching { QuoteHttp.request(BitcoinQuotes.MERCADO_URL) { uri ->
            opened++; Response(uri, 302, headers = mapOf("Location" to "/redirect/$opened"))
        } }.isFailure)
        assertEquals(4, opened)
        assertTrue(runCatching { QuoteHttp.request(BitcoinQuotes.MERCADO_URL) { uri -> ok(uri, "<html>Login</html>") } }.exceptionOrNull()!!.message!!.contains("HTML"))
        assertTrue(runCatching { QuoteHttp.request(BitcoinQuotes.COINBASE_URL) { uri ->
            Response(uri,200,"{}",mapOf("Age" to "600"))
        } }.exceptionOrNull()!!.message!!.contains("cache"))
    }

    @Test fun legacyPublicTickerRecoversWhenV4RedirectIsUnavailableAndCoinbaseFails() {
        val quote = BitcoinQuotes.fetch { url -> QuoteHttp.request(url) { uri -> when(url) {
            BitcoinQuotes.MERCADO_URL -> Response(uri,302,headers = mapOf("Location" to "https://unavailable.example/"))
            BitcoinQuotes.MERCADO_LEGACY_URL -> ok(uri, """{"ticker":{"last":"500000.25","date":${now.epochSecond}}}""")
            else -> throw AssertionError("Legacy source should recover before Coinbase")
        } } }
        assertEquals("500000.25", quote.price)
        assertEquals("Mercado Bitcoin · BTC/BRL", quote.source)
    }
}
