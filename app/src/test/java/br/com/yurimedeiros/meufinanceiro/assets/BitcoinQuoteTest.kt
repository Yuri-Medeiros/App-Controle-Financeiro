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
}
