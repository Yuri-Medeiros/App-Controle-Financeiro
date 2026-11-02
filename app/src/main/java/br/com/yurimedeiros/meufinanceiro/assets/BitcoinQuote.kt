package br.com.yurimedeiros.meufinanceiro.assets

import org.json.JSONArray
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

internal data class BitcoinQuote(val price: String, val timestamp: String) {
    companion object {
        fun validated(pair: String, value: String, date: Long, now: Instant = Instant.now()): BitcoinQuote {
            require(pair == "BTC-BRL")
            val price = BigDecimal(value)
            require(price.signum() > 0 && price.precision() <= 20 && price.scale() in 0..8)
            // MB examples use seconds, while the schema also documents nanoseconds.
            val timestamp = if (date > 100_000_000_000L) Instant.ofEpochSecond(date / 1_000_000_000L, date % 1_000_000_000L) else Instant.ofEpochSecond(date)
            require(!timestamp.isAfter(now.plusSeconds(60)) && !timestamp.isBefore(now.minusSeconds(300)))
            return BitcoinQuote(price.toPlainString(), timestamp.toString())
        }
    }
}

/** Public market data only: no credentials, wallet addresses or holdings are sent. */
internal object BitcoinQuotes {
    fun fetch(): BitcoinQuote {
        val connection = URI("https://api.mercadobitcoin.net/api/v4/tickers?symbols=BTC-BRL").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            check(connection.responseCode == 200) { "Cotação indisponível. Tente novamente mais tarde." }
            val bytes = connection.inputStream.use { it.readNBytes(65537) }
            require(bytes.size <= 65536)
            val row = JSONArray(bytes.toString(Charsets.UTF_8)).getJSONObject(0)
            return BitcoinQuote.validated(row.getString("pair"), row.getString("last"), row.getLong("date"))
        } finally { connection.disconnect() }
    }
}
