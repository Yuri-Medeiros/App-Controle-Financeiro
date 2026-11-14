package br.com.yurimedeiros.meufinanceiro.assets

import java.util.Locale
import java.util.UUID

internal enum class InvestmentType(val label: String) {
    RDB("RDB"), TREASURY("Tesouro Direto"), ETF("ETFs"), STOCK("Ações"), OTHER("Outros")
}

internal object InvestmentPresentation {
    private val ticker = Regex("(?:^|[\\s(])([A-Z][A-Z0-9]{3}[0-9]{1,2}F?)\\)?\\s*$", RegexOption.IGNORE_CASE)
    fun label(asset: AssetPosition): String = if (asset.kind == AssetKind.INVESTMENT)
        ticker.find(asset.name)?.groupValues?.get(1)?.uppercase(Locale.ROOT) ?: asset.name else asset.name

    fun type(asset: AssetPosition): InvestmentType {
        asset.investmentType?.let { return it }
        // Legacy custody records predate the explicit type field. Only infer their
        // documented sections when the stable import identity matches the full name.
        val importedId = UUID.nameUUIDFromBytes("nubank-custody:${asset.name}".toByteArray(Charsets.UTF_8)).toString()
        if (asset.kind != AssetKind.INVESTMENT || asset.id != importedId) return InvestmentType.OTHER
        return when {
            asset.name.startsWith("Caixinha ") -> InvestmentType.RDB
            Regex("Tesouro (?:IPCA\\+|Selic|Prefixado) [0-9]{4}").matches(asset.name) -> InvestmentType.TREASURY
            ticker.containsMatchIn(asset.name) -> InvestmentType.ETF
            else -> InvestmentType.OTHER
        }
    }

    fun totalsByType(assets: List<AssetPosition>): Map<InvestmentType, Long> = assets
        .filter { it.kind == AssetKind.INVESTMENT && !it.disposed }
        .groupBy(::type).mapValues { (_, positions) -> positions.sumOf { it.valueCents ?: 0L } }.filterValues { it > 0 }
}
