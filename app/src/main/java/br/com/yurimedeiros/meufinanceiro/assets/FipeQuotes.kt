package br.com.yurimedeiros.meufinanceiro.assets

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONException
import java.math.BigDecimal
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

internal enum class FipeType(val key: String, val apiKey: String, val label: String, val number: Int) {
    CAR("carros", "cars", "Carro", 1), MOTORCYCLE("motos", "motorcycles", "Moto", 2), TRUCK("caminhoes", "trucks", "Caminhão", 3)
}
internal data class FipeVehicle(val type: FipeType, val brand: String, val model: String, val year: String) {
    val path: String get() = "${type.key}/marcas/$brand/modelos/$model/anos/$year"
    val apiPath: String get() = "${type.apiKey}/brands/$brand/models/$model/years/$year"
    companion object {
        fun parse(path: String): FipeVehicle? {
            val match = Regex("(carros|motos|caminhoes)/marcas/([0-9]+)/modelos/([0-9]+)/anos/((?:[0-9]{4}|32000)-[0-9]+)").matchEntire(path) ?: return null
            val (type, brand, model, year) = match.destructured
            return FipeVehicle(FipeType.entries.single { it.key == type }, brand, model, year)
        }
    }
}
internal data class FipeOption(val code: String, val name: String)
internal data class FipeQuote(val cents: Long, val reference: YearMonth, val timestamp: String, val source: String, val vehiclePath: String)
internal class FipeValidationException(message: String) : IllegalArgumentException(message)

internal object FipeQuotes {
    const val BASE = "https://fipe.parallelum.com.br/api/v2"
    private const val LEGACY_BASE = "https://parallelum.com.br/fipe/api/v1"

    // Only transport/server failures permit a second request. Never bypass a quota or access denial.
    private fun readWithFallback(path: String, legacyPath: String, read: (String) -> String): Pair<String, Boolean> {
        try { return read("$BASE/$path") to false }
        catch (error: IOException) {
            if (error is QuoteHttpException && error.status !in 500..599) throw error
            try { return read("$LEGACY_BASE/$legacyPath") to true }
            catch (fallback: Exception) { fallback.addSuppressed(error); throw fallback }
        }
    }

    private val months = listOf("janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun catalog(path: String, read: (String) -> String = { QuoteHttp.request(it).body }): List<FipeOption> {
        require(Regex("(cars|motorcycles|trucks)/brands(?:/[0-9]+/models(?:/[0-9]+/years)?)?").matches(path))
        val years = path.endsWith("/years")
        val type = FipeType.entries.single { it.apiKey == path.substringBefore('/') }
        val legacyPath = path.replaceBefore('/', type.key).replace("/brands", "/marcas")
            .replace("/models", "/modelos").replace("/years", "/anos")
        val (body, legacy) = readWithFallback(path, legacyPath, read)
        val rows = if (legacy && path.endsWith("/models")) JSONObject(body).getJSONArray("modelos") else JSONArray(body)
        val codeKey = if (legacy) "codigo" else "code"
        val nameKey = if (legacy) "nome" else "name"
        require(rows.length() in 1..10000) { "Lista FIPE indisponível." }
        val options = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            require(!row.isNull(codeKey) && !row.isNull(nameKey)) { "Lista FIPE inválida." }
            val code = row.get(codeKey).toString()
            require((if (years) Regex("(?:[0-9]{4}|32000)-[0-9]+") else Regex("[0-9]+")).matches(code))
            FipeOption(code, row.getString(nameKey).trim().also { require(it.isNotBlank() && it.length <= 300) })
        }
        require(options.map { it.code }.distinct().size == options.size)
        return options
    }

    fun fetch(path: String, today: LocalDate = LocalDate.now(), read: (String) -> String = { QuoteHttp.request(it).body }): FipeQuote {
        val vehicle = requireNotNull(FipeVehicle.parse(path)) { "Configure a identificação FIPE do veículo." }
        val (body, legacy) = readWithFallback(vehicle.apiPath, vehicle.path, read)
        val normalized = if (legacy) {
            val original = JSONObject(body)
            val row = JSONObject()
            mapOf("TipoVeiculo" to "vehicleType", "Valor" to "price", "Marca" to "brand",
                "Modelo" to "model", "AnoModelo" to "modelYear", "SiglaCombustivel" to "fuelAcronym",
                "CodigoFipe" to "codeFipe", "MesReferencia" to "referenceMonth").forEach { (from, to) ->
                row.put(to, original.get(from))
            }
            row.toString()
        } else body
        return parse(normalized, vehicle, today)
    }

    fun parse(body: String, vehicle: FipeVehicle, today: LocalDate, now: Instant = Instant.now()): FipeQuote {
        val row = JSONObject(body)
        require(row.get("vehicleType").toString() == vehicle.type.number.toString() && row.get("modelYear").toString() == vehicle.year.substringBefore('-')) {
            "A resposta FIPE não corresponde ao veículo selecionado."
        }
        val fuels = mapOf("1" to "G", "2" to "A", "3" to "D", "4" to "E", "5" to "F", "6" to "H")
        require(row.getString("fuelAcronym").uppercase(Locale.ROOT) == fuels[vehicle.year.substringAfter('-')]) {
            "A resposta FIPE não corresponde ao combustível selecionado."
        }
        val rawReference = normalize(row.getString("referenceMonth").trim()).replace(Regex("\\s+"), " ")
        val parts = Regex("([a-z]+)(?: de |/| )([0-9]{4})").matchEntire(rawReference)
            ?: throw FipeValidationException("Mês de referência FIPE inválido.")
        val month = months.indexOf(parts.groupValues[1]) + 1
        require(month in 1..12) { "Mês de referência FIPE inválido." }
        val reference = YearMonth.of(parts.groupValues[2].toInt(), month)
        if (reference != YearMonth.from(today)) throw FipeValidationException("A FIPE ainda não retornou uma referência válida para o mês atual.")
        val price = row.getString("price").trim().replace('\u00a0', ' ')
        require(Regex("R\\$\\s*(?:[0-9]{1,3}(?:\\.[0-9]{3})+|[0-9]+),[0-9]{2}").matches(price)) { "Valor FIPE inválido." }
        val cents = BigDecimal(price.removePrefix("R$").trim().replace(".", "").replace(',', '.')).movePointRight(2).longValueExact()
        require(cents in 1..100000000000000L) { "Valor FIPE fora do intervalo permitido." }
        val code = row.getString("codeFipe")
        require(Regex("[0-9]{6}-[0-9]").matches(code)) { "Código FIPE inválido." }
        require(!row.isNull("brand") && !row.isNull("model")) { "Identificação FIPE incompleta." }
        val brand = row.getString("brand").trim(); val model = row.getString("model").trim()
        require(brand.isNotBlank() && model.isNotBlank() && brand.length <= 300 && model.length <= 300)
        return FipeQuote(cents, reference, now.toString(), "FIPE (Parallelum) · $brand $model · $code · ${row.getString("referenceMonth")}", vehicle.path)
    }

    fun due(asset: AssetPosition, today: LocalDate): Boolean = asset.kind == AssetKind.VEHICLE && !asset.disposed &&
        asset.vehiclePath.isNotBlank() && asset.fipeReference != YearMonth.from(today).toString()

    fun action(original: AssetPosition, current: AssetPosition, quote: FipeQuote, today: LocalDate): AssetChange.Quote {
        if (original != current || current.disposed || current.kind != AssetKind.VEHICLE) throw FipeValidationException("O veículo foi alterado durante a consulta. Atualize novamente.")
        if (current.confirmedDate.isAfter(today)) throw FipeValidationException("A data confirmada do veículo está no futuro. Confira os dados antes de atualizar.")
        require(current.vehiclePath == quote.vehiclePath && quote.reference == YearMonth.from(today)) { "A cotação FIPE não corresponde à configuração atual." }
        require(current.fipeReference?.let { quote.reference >= YearMonth.parse(it) } != false) { "A referência FIPE salva é mais recente." }
        return AssetChange.Quote(current.id, quote.cents, null, quote.timestamp, quote.source, quote.reference.toString(), quote.vehiclePath)
    }

    fun failure(error: Exception): String = when {
        error is QuoteHttpException && error.status == 429 -> "Limite gratuito da FIPE atingido. Tente novamente amanhã."
        error is QuoteHttpException && error.status == 404 -> "Veículo não encontrado na FIPE. Confira marca, modelo, ano e combustível."
        error is QuoteHttpException && error.status in listOf(401, 403) -> "A FIPE recusou o acesso (HTTP ${error.status}). Tente novamente mais tarde."
        error is QuoteHttpException -> "Serviço FIPE indisponível (HTTP ${error.status}). Tente novamente mais tarde."
        error is UnknownHostException -> "Não foi possível localizar o serviço FIPE. Confira sua conexão e o DNS."
        error is SocketTimeoutException -> "O serviço FIPE demorou para responder. Tente novamente."
        error is SSLException -> "Não foi possível estabelecer uma conexão segura com a FIPE."
        error is JSONException || error is QuoteResponseException -> "O serviço FIPE retornou uma resposta inválida. Tente novamente mais tarde."
        error is FipeValidationException -> error.message ?: "Referência FIPE inválida."
        else -> "Não foi possível consultar a FIPE. Confira sua conexão e tente novamente."
    }
}
