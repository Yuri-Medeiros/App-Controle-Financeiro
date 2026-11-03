package br.com.yurimedeiros.meufinanceiro.assets

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

internal data class BitcoinQuote(val price: String, val timestamp: String, val source: String = "Mercado Bitcoin · BTC/BRL") {
    companion object {
        fun validated(pair: String, value: String, date: Long, now: Instant = Instant.now(), source: String = "Mercado Bitcoin · BTC/BRL"): BitcoinQuote {
            require(pair == "BTC-BRL" && value.length <= 128 && date > 0)
            val price = BigDecimal(value).stripTrailingZeros()
            require(price.signum() > 0 && price < BigDecimal("1000000000000") && price.scale() <= 18)
            val timestamp = when {
                date >= 100_000_000_000_000_000L -> Instant.ofEpochSecond(date / 1_000_000_000L, date % 1_000_000_000L)
                date >= 100_000_000_000_000L -> Instant.ofEpochSecond(date / 1_000_000L, (date % 1_000_000L) * 1000)
                date >= 100_000_000_000L -> Instant.ofEpochMilli(date)
                else -> Instant.ofEpochSecond(date)
            }
            require(!timestamp.isAfter(now.plusSeconds(60)) && !timestamp.isBefore(now.minusSeconds(300))) { "Cotação antiga ou com data inválida." }
            return BitcoinQuote(price.toPlainString(), timestamp.toString(), source)
        }
    }
}

internal class QuoteHttpException(val status: Int) : java.io.IOException("HTTP $status")

internal data class QuoteResponse(val body: String, val serverTime: Instant) {
    companion object {
        fun fromHttp(body: String, dateMillis: Long, ageSeconds: Long, receivedAt: Instant = Instant.now()): QuoteResponse {
            require(ageSeconds in 0..299) { "Resposta em cache expirada." }
            val serverTime = if (dateMillis > 0) Instant.ofEpochMilli(dateMillis).plusSeconds(ageSeconds) else receivedAt
            return QuoteResponse(body, serverTime)
        }
    }
}
internal class BitcoinQuoteUnavailable(val failures: List<String>) : IllegalStateException(failures.joinToString("; "))

/** Requests contain only the public BTC/BRL pair, never credentials or holdings. */
internal object BitcoinQuotes {
    const val MERCADO_URL = "https://api.mercadobitcoin.net/api/v4/tickers?symbols=BTC-BRL"
    const val COINBASE_URL = "https://api.coinbase.com/v2/prices/BTC-BRL/spot"

    fun fetch(request: (String) -> QuoteResponse = ::request): BitcoinQuote {
        val failures = mutableListOf<String>()
        for ((name, url) in listOf("Mercado Bitcoin" to MERCADO_URL, "Coinbase" to COINBASE_URL)) {
            try {
                val response = request(url)
                return if (url == MERCADO_URL) mercado(response) else coinbase(response)
            } catch (error: Exception) {
                val reason = when (error) {
                    is QuoteHttpException -> "serviço retornou HTTP ${error.status}"
                    is java.net.UnknownHostException -> "DNS indisponível"
                    is java.net.SocketTimeoutException -> "tempo de conexão esgotado"
                    is javax.net.ssl.SSLException -> "falha na conexão segura"
                    is java.io.IOException -> "falha de conexão"
                    else -> "resposta indisponível ou inválida"
                }
                failures += "$name: $reason"
            }
        }
        throw BitcoinQuoteUnavailable(failures)
    }

    private fun mercado(response: QuoteResponse): BitcoinQuote {
        val rows = JSONArray(response.body)
        val row = (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.optString("pair") == "BTC-BRL" }
        return BitcoinQuote.validated(row.getString("pair"), row.getString("last"), row.getLong("date"), response.serverTime)
    }

    private fun coinbase(response: QuoteResponse): BitcoinQuote {
        val row = JSONObject(response.body).getJSONObject("data")
        require(row.getString("currency") == "BRL" && row.optString("base", "BTC") == "BTC")
        // Spot has no trade timestamp: record the HTTP server's consultation time.
        return BitcoinQuote.validated("BTC-BRL", row.getString("amount"), response.serverTime.epochSecond, response.serverTime, "Coinbase · BTC/BRL")
    }

    private fun request(url: String): QuoteResponse {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("User-Agent", "MeuFinanceiro/Android")
            val status = connection.responseCode
            if (status != 200) throw QuoteHttpException(status)
            val bytes = connection.inputStream.use { it.readNBytes(65537) }
            require(bytes.size <= 65536)
            val age = connection.getHeaderField("Age")?.toLongOrNull() ?: 0L
            val date = connection.getHeaderFieldDate("Date", -1L)
            return QuoteResponse.fromHttp(bytes.toString(Charsets.UTF_8), date, age)
        } finally { connection.disconnect() }
    }
}
