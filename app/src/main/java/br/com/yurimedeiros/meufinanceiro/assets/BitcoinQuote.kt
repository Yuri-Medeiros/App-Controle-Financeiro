package br.com.yurimedeiros.meufinanceiro.assets

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

internal data class BitcoinQuote(val price: String, val timestamp: String, val source: String = "Mercado Bitcoin · BTC/BRL", val stale: Boolean = false) {
    companion object {
        fun validated(pair: String, value: String, date: Long, now: Instant = Instant.now(), source: String = "Mercado Bitcoin · BTC/BRL", allowReference: Boolean = false): BitcoinQuote {
            require(pair == "BTC-BRL" && value.length <= 128 && date > 0)
            val price = try { BigDecimal(value).stripTrailingZeros() } catch (_: NumberFormatException) { throw QuoteResponseException("preço não numérico") }
            if (price.signum() <= 0 || price >= BigDecimal("1000000000000") || price.scale() > 100) {
                throw QuoteResponseException("preço fora do intervalo permitido")
            }
            val timestamp = when {
                date >= 100_000_000_000_000_000L -> Instant.ofEpochSecond(date / 1_000_000_000L, date % 1_000_000_000L)
                date >= 100_000_000_000_000L -> Instant.ofEpochSecond(date / 1_000_000L, (date % 1_000_000L) * 1000)
                date >= 100_000_000_000L -> Instant.ofEpochMilli(date)
                else -> Instant.ofEpochSecond(date)
            }
            val maxAge = if (allowReference) 86400L else 300L
            if (timestamp.isAfter(now.plusSeconds(60)) || timestamp.isBefore(now.minusSeconds(maxAge))) {
                throw QuoteResponseException("cotação com data inválida ou anterior ao limite de referência")
            }
            return BitcoinQuote(price.toPlainString(), timestamp.toString(), source, timestamp.isBefore(now.minusSeconds(300)))
        }
    }
}

internal class QuoteResponseException(message: String) : IllegalArgumentException(message)

internal class QuoteHttpException(val status: Int) : java.io.IOException("HTTP $status")

internal data class QuoteResponse(val body: String, val serverTime: Instant, val priceTime: Instant = serverTime) {
    companion object {
        fun fromHttp(body: String, dateMillis: Long, ageSeconds: Long, receivedAt: Instant = Instant.now()): QuoteResponse {
            if (ageSeconds !in 0..86400) throw QuoteResponseException("cache do serviço com data inválida ou superior a 24 horas")
            val priceTime = if (dateMillis > 0) Instant.ofEpochMilli(dateMillis) else receivedAt.minusSeconds(ageSeconds)
            return QuoteResponse(body, priceTime.plusSeconds(ageSeconds), priceTime)
        }
    }
}
internal class BitcoinQuoteUnavailable(val failures: List<String>) : IllegalStateException(failures.joinToString("; "))

/** Requests contain only the public BTC/BRL pair, never credentials or holdings. */
internal object BitcoinQuotes {
    const val COINBASE_RATES_URL = "https://api.coinbase.com/v2/exchange-rates?currency=BTC"
    const val MERCADO_URL = "https://api.mercadobitcoin.net/api/v4/tickers?symbols=BTC-BRL"
    const val MERCADO_LEGACY_URL = "https://www.mercadobitcoin.net/api/BTC/ticker/"
    const val COINBASE_URL = "https://api.coinbase.com/v2/prices/BTC-BRL/spot"

    fun fetch(request: (String) -> QuoteResponse = { QuoteHttp.request(it) }): BitcoinQuote {
        val failures = mutableListOf<String>()
        var reference: BitcoinQuote? = null
        for ((name, url) in listOf("Mercado Bitcoin" to MERCADO_URL, "Mercado Bitcoin (público)" to MERCADO_LEGACY_URL, "Coinbase" to COINBASE_URL, "Coinbase (conversão)" to COINBASE_RATES_URL)) {
            try {
                val response = request(url)
                val quote = when (url) {
                    COINBASE_URL -> coinbase(response)
                    COINBASE_RATES_URL -> coinbaseRates(response)
                    else -> mercado(response)
                }
                if (!quote.stale) return quote
                if (reference == null || Instant.parse(quote.timestamp) > Instant.parse(reference.timestamp)) reference = quote
            } catch (error: Exception) {
                val reason = when (error) {
                    is QuoteResponseException -> error.message ?: "resposta inválida"
                    is org.json.JSONException -> "JSON fora do formato esperado"
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
        return reference ?: throw BitcoinQuoteUnavailable(failures)
    }

    private fun mercado(response: QuoteResponse): BitcoinQuote {
        val row = if (response.body.trimStart().startsWith("[")) {
            val rows = JSONArray(response.body)
            (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.optString("pair") == "BTC-BRL" }
        } else JSONObject(response.body).getJSONObject("ticker")
        val pair = if (row.isNull("pair")) "BTC-BRL" else row.getString("pair")
        return BitcoinQuote.validated(pair, row.getString("last"), row.getLong("date"), response.serverTime, allowReference = true)
    }

    private fun coinbaseRates(response: QuoteResponse): BitcoinQuote {
        val row = JSONObject(response.body).getJSONObject("data")
        require(row.getString("currency") == "BTC") { "Base de conversão diferente de BTC." }
        val amount = row.getJSONObject("rates").getString("BRL")
        return BitcoinQuote.validated("BTC-BRL", amount, response.priceTime.epochSecond, response.serverTime, "Coinbase · BTC/BRL", allowReference = true)
    }

    private fun coinbase(response: QuoteResponse): BitcoinQuote {
        val row = JSONObject(response.body).getJSONObject("data")
        // Android optString returns the literal "null" for JSONObject.NULL, not its fallback.
        val base = if (row.isNull("base")) "BTC" else row.getString("base")
        if (row.getString("currency") != "BRL" || base != "BTC") {
            throw QuoteResponseException("a resposta não corresponde ao par BTC/BRL")
        }
        // Spot has no trade timestamp: record the HTTP server's consultation time.
        return BitcoinQuote.validated("BTC-BRL", row.getString("amount"), response.priceTime.epochSecond, response.serverTime, "Coinbase · BTC/BRL", allowReference = true)
    }

}

/** The same transport is exercised by JVM tests, including HTTP 302 response handling. */
internal object QuoteHttp {
    private val redirectCodes = setOf(301, 302, 303, 307, 308)

    fun request(url: String, open: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }): QuoteResponse {
        val origin = URI(url)
        requireAllowed(origin, origin)
        var target = origin
        val visited = mutableSetOf<URI>()
        repeat(4) { hop ->
            if (!visited.add(target)) throw QuoteResponseException("redirecionamento circular")
            val connection = open(target)
            try {
                connection.connectTimeout = 5000; connection.readTimeout = 5000
                connection.instanceFollowRedirects = false // Follow only validated HTTPS destinations below.
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("User-Agent", "MeuFinanceiro/Android")
                val status = connection.responseCode
                if (status in redirectCodes) {
                    if (hop == 3) throw QuoteResponseException("limite de redirecionamentos excedido")
                    val location = connection.getHeaderField("Location")?.takeIf { it.isNotBlank() }
                        ?: throw QuoteResponseException("redirecionamento sem destino")
                    val next = target.resolve(location)
                    requireAllowed(origin, next)
                    target = next
                } else {
                    if (status != 200) throw QuoteHttpException(status)
                    val bodyLimit = if (origin.host in setOf("fipe.parallelum.com.br", "parallelum.com.br")) 524288 else if (origin.path == "/v2/exchange-rates") 262144 else 65536
                    val bytes = connection.inputStream.use { it.readNBytes(bodyLimit + 1) }
                    if (bytes.size > bodyLimit) throw QuoteResponseException("resposta acima do limite")
                    val body = bytes.toString(Charsets.UTF_8)
                    // Some public endpoints label valid JSON as text/html; validate the body itself.
                    if (body.trimStart().startsWith("<")) {
                        throw QuoteResponseException("serviço devolveu página HTML em vez da cotação")
                    }
                    val age = connection.getHeaderField("Age")?.toLongOrNull() ?: 0L
                    val date = connection.getHeaderFieldDate("Date", -1L)
                    return QuoteResponse.fromHttp(body, date, age)
                }
            } finally { connection.disconnect() }
        }
        throw QuoteResponseException("limite de redirecionamentos excedido")
    }

    private fun requireAllowed(origin: URI, target: URI) {
        val host = target.host?.lowercase().orEmpty()
        val mercado = origin.host == "api.mercadobitcoin.net" || origin.host == "www.mercadobitcoin.net"
        val allowed = if (mercado) listOf("mercadobitcoin.net", "mercadobitcoin.com.br").any { host == it || host.endsWith(".$it") }
            else if (origin.host in setOf("fipe.parallelum.com.br", "parallelum.com.br")) host in setOf("fipe.parallelum.com.br", "parallelum.com.br")
            else origin.host == "api.coinbase.com" && host == "api.coinbase.com"
        if (!target.scheme.equals("https", ignoreCase = true) || target.userInfo != null || target.port !in listOf(-1,443) || !allowed) {
            throw QuoteResponseException("destino de redirecionamento não permitido")
        }
    }
}
