package br.com.yurimedeiros.meufinanceiro.ui

import br.com.yurimedeiros.meufinanceiro.assets.AssetPosition
import br.com.yurimedeiros.meufinanceiro.formatting.AppDates
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

internal data class VehicleValuationDetails(val model: String, val updated: String, val source: String, val reference: String) {
    companion object {
        fun from(asset: AssetPosition, zone: ZoneId = ZoneId.systemDefault()): VehicleValuationDetails {
            // Existing FIPE records retain the provider's model description in this source segment.
            val parts = asset.quoteSource.orEmpty().split(" · ")
            val isFipe = parts.size == 4 && parts[0] == "FIPE (Parallelum)" && Regex("[0-9]{6}-[0-9]").matches(parts[2])
            return VehicleValuationDetails(
                model = if (isFipe) parts[1] else asset.name,
                updated = runCatching { AppDates.date(Instant.parse(asset.quotedAt).atZone(zone).toLocalDate()) }.getOrDefault("Não disponível"),
                source = if (isFipe) parts[0] else "Não disponível",
                reference = runCatching { AppDates.month(YearMonth.parse(asset.fipeReference)) }.getOrDefault("Não disponível"),
            )
        }
    }
}
