package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.assets.*
import java.util.Locale

@Composable
internal fun AssetCharts(book: AssetBook, individualAssets: Boolean = false) {
    val live = book.assets.filter { !it.disposed }
    val composition = live.filter { it.valueCents != null }.groupBy { if (individualAssets) it.id else it.kind.name }
        .mapValues { (_, rows) -> rows.sumOf { it.valueCents ?: 0L } }.filterValues { it > 0 }
    val total = composition.values.sum()
    val points = remember(book.history) { AssetInsights.evolution(book) }
    val keys = book.assets.map { if (individualAssets) it.id else it.kind.name }.distinct()
    val colors = keys.mapIndexed { index, key -> key to (NeonPalette.getOrNull(index)
        ?: Color.hsl(((index - NeonPalette.size) * 137.508f + 35f) % 360f, 0.7f, 0.55f)) }.toMap()
    val labels = if (individualAssets) book.assets.associate { it.id to InvestmentPresentation.label(it) } else
        mapOf(AssetKind.INVESTMENT.name to "Investimentos", AssetKind.BITCOIN.name to "Bitcoin",
            AssetKind.FGTS.name to "FGTS", AssetKind.VEHICLE.name to "Veículo")

    GlassPanel {
        Text("Distribuição", style = MaterialTheme.typography.titleMedium)
        if (total == 0L) Text("Ainda não há valores disponíveis para comparar.")
        else if (individualAssets) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InteractiveDonutChart("Por investimento", composition.map { (id, cents) ->
                    DonutSlice(id, labels.getValue(id), cents, colors.getValue(id))
                }, Modifier.weight(1f))
                InteractiveDonutChart("Por tipo", InvestmentPresentation.totalsByType(live).map { (type, cents) ->
                    DonutSlice(type.name, type.label, cents, NeonPalette[type.ordinal % NeonPalette.size])
                }, Modifier.weight(1f))
            }
        }
        else {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val chartSize = (maxWidth * 0.38f).coerceAtMost(132.dp)
                val legendWidth = (maxWidth - chartSize - 16.dp).coerceAtLeast(0.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Canvas(Modifier.size(chartSize).padding(10.dp).semantics {
                        contentDescription = if (individualAssets) "Distribuição por investimento; valores e percentuais na legenda ao lado" else "Distribuição do patrimônio por tipo; valores e percentuais na legenda ao lado"
                    }) {
                        var start = -90f
                        composition.entries.forEach { (kind, cents) ->
                            val sweep = (cents.toDouble() / total * 360).toFloat()
                            drawArc(colors.getValue(kind), start, sweep, false, style = Stroke(size.minDimension * 0.16f))
                            start += sweep
                        }
                    }
                    Column(Modifier.widthIn(max = legendWidth).width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        composition.entries.forEach { (kind, cents) ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Canvas(Modifier.padding(top = 5.dp).size(6.dp)) { drawCircle(colors.getValue(kind)) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(labels.getValue(kind), style = MaterialTheme.typography.bodySmall)
                                    Text(ledgerMoney(cents) + " · " + String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", cents.toDouble() / total * 100),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (live.any { it.valueCents == null }) ChartNote("Composição parcial: patrimônios sem cotação não entram nos percentuais.")
    }
    GlassPanel {
        Text("Evolução", style = MaterialTheme.typography.titleMedium)
        EvolutionSeriesChart(points.map { point -> EvolutionSeriesPoint(point.date,
            if (individualAssets) point.byAsset else point.byKind.mapKeys { it.key.name }) }, colors, labels)
    }
}

@Composable
private fun ChartNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
