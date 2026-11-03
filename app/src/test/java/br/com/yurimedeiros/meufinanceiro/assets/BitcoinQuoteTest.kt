package br.com.yurimedeiros.meufinanceiro.assets

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class BitcoinQuoteTest {
    private val now = Instant.parse("2026-11-02T12:00:00Z")
    @Test fun acceptsBrlQuoteWithSecondsOrNanosecondsWithoutFloatingPointRounding() {
        val seconds = BitcoinQuote.validated("BTC-BRL", "500123.12345678", now.epochSecond, now)
        val nanos = BitcoinQuote.validated("BTC-BRL", "500123.12345678", now.epochSecond * 1_000_000_000L, now)
        assertEquals(seconds, nanos)
        assertEquals("500123.12345678", seconds.price)
    }
    @Test fun rejectsWrongPairStaleOrFutureQuotesAndInvalidPrices() {
        assertTrue(runCatching { BitcoinQuote.validated("BTC-USD", "500", now.epochSecond, now) }.isFailure)
        assertTrue(runCatching { BitcoinQuote.validated("BTC-BRL", "500", now.epochSecond - 301, now) }.isFailure)
        assertTrue(runCatching { BitcoinQuote.validated("BTC-BRL", "500", now.epochSecond + 61, now) }.isFailure)
        for (value in listOf("0", "-1", "NaN", "1E100", "9999999999999999999999999")) {
            assertTrue(runCatching { BitcoinQuote.validated("BTC-BRL", value, now.epochSecond, now) }.isFailure)
        }
    }
    @Test fun acceptsMillisecondsAndMicrosecondsFromMarketProviders() {
        val seconds = BitcoinQuote.validated("BTC-BRL", "500000.0000000000000000", now.epochSecond, now)
        assertEquals(seconds, BitcoinQuote.validated("BTC-BRL", "500000", now.toEpochMilli(), now))
        assertEquals(seconds, BitcoinQuote.validated("BTC-BRL", "500000", now.epochSecond * 1_000_000L, now))
        assertEquals("500000", seconds.price)
    }

    @Test fun parsesMarketJsonAndValuesHoldingsExactly() {
        val quote = BitcoinQuotes.fetch { url ->
            assertEquals(BitcoinQuotes.MERCADO_URL, url)
            QuoteResponse("""[{"pair":"ETH-BRL","last":"1000","date":${now.epochSecond}},
                {"pair":"BTC-BRL","last":"500123.12345678","date":${now.toEpochMilli()}}]""", now)
        }
        val asset = AssetPosition("btc", "Bitcoin", AssetKind.BITCOIN, 0, 10000000, java.time.LocalDate.of(2026,11,2))
        val book = AssetRules.apply(AssetBook(listOf(asset)), AssetChange.Quote(asset.id, 0, quote.price, quote.timestamp, quote.source))
        assertEquals(5001231L, book.assets.single().valueCents)
        assertEquals(10000000L, book.assets.single().satoshis)
    }

    @Test fun fallsBackToPublicSpotWhenMarketFailsAndRecordsActualSource() {
        val calls = mutableListOf<String>()
        val quote = BitcoinQuotes.fetch { url ->
            calls += url
            if (url == BitcoinQuotes.MERCADO_URL) throw QuoteHttpException(503)
            QuoteResponse("""{"data":{"base":"BTC","currency":"BRL","amount":"500000.25"}}""", now)
        }
        assertEquals(listOf(BitcoinQuotes.MERCADO_URL, BitcoinQuotes.MERCADO_LEGACY_URL, BitcoinQuotes.COINBASE_URL), calls)
        assertEquals("500000.25", quote.price)
        assertEquals("Coinbase · BTC/BRL", quote.source)
        assertEquals(now.toString(), quote.timestamp)
    }

    @Test fun malformedStaleAndWrongCurrencyResponsesCannotBecomeBalances() {
        for (primary in listOf("invalid JSON", """[{"pair":"BTC-BRL","last":"500000","date":${now.epochSecond-301}}]""")) {
            val quote = BitcoinQuotes.fetch { url -> QuoteResponse(if (url == BitcoinQuotes.MERCADO_URL) primary else
                """{"data":{"currency":"BRL","amount":"500000"}}""", now) }
            assertEquals("Coinbase · BTC/BRL", quote.source)
        }
        val result = runCatching { BitcoinQuotes.fetch { url ->
            if (url == BitcoinQuotes.MERCADO_URL) throw java.net.UnknownHostException()
            QuoteResponse("""{"data":{"base":"BTC","currency":"USD","amount":"500000"}}""", now)
        } }
        val error = result.exceptionOrNull() as BitcoinQuoteUnavailable
        assertEquals(3, error.failures.size)
        assertTrue(error.message!!.contains("DNS"))
    }
    @Test fun serverClockWinsOverWrongDeviceClockAndExpiredHttpCacheIsRejected() {
        val response = QuoteResponse.fromHttp("[]", now.toEpochMilli(), 10, now.plusSeconds(86400))
        assertEquals(now.plusSeconds(10), response.serverTime)
        val quote = BitcoinQuote.validated("BTC-BRL", "500000", now.epochSecond, response.serverTime)
        assertEquals(now.toString(), quote.timestamp)
        assertTrue(runCatching { QuoteResponse.fromHttp("[]", now.toEpochMilli(), 300, now) }.isFailure)
        assertEquals(now, QuoteResponse.fromHttp("[]", -1, 0, now).serverTime)
    }
}
