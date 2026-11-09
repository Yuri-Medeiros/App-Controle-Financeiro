package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.assets.*
import br.com.yurimedeiros.meufinanceiro.formatting.AppDates
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun AssetCharts(book: AssetBook) {
    val live = book.assets.filter { !it.disposed }
    val composition = live.filter { it.valueCents != null }.groupBy { it.kind }
        .mapValues { (_, rows) -> rows.sumOf { it.valueCents ?: 0L } }.filterValues { it > 0 }
    val total = composition.values.sum()
    val points = remember(book.history) { AssetInsights.evolution(book) }
    val today = LocalDate.now()
    val flows = remember(book.history, today) { AssetInsights.flows(book, today) }
    val labels = mapOf(AssetKind.INVESTMENT to "Investimentos", AssetKind.BITCOIN to "Bitcoin",
        AssetKind.FGTS to "FGTS", AssetKind.VEHICLE to "Veículo")

    GlassPanel {
        Text("Distribuição", style = MaterialTheme.typography.titleMedium)
        if (total == 0L) Text("Ainda não há valores disponíveis para comparar.")
        else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Canvas(Modifier.weight(0.38f).aspectRatio(1f).padding(10.dp).semantics {
                    contentDescription = "Distribuição do patrimônio por tipo; valores e percentuais na legenda ao lado"
                }) {
                    var start = -90f
                    composition.entries.forEachIndexed { index, (_, cents) ->
                        val sweep = (cents.toDouble() / total * 360).toFloat()
                        drawArc(NeonPalette[index], start, sweep, false, style = Stroke(size.minDimension * 0.16f))
                        start += sweep
                    }
                }
                Column(Modifier.weight(0.62f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    composition.entries.forEachIndexed { index, (kind, cents) ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Canvas(Modifier.padding(top = 5.dp).size(6.dp)) { drawCircle(NeonPalette[index]) }
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
        if (live.any { it.valueCents == null }) ChartNote("Composição parcial: patrimônios sem cotação não entram nos percentuais.")
    }
    GlassPanel {
        Text("Evolução registrada", style = MaterialTheme.typography.titleMedium)
        // Partial totals would create artificial drops when a Bitcoin price is missing.
        val complete = points.filter { !it.partial }
        if (complete.size < 2) Text("Registre valores em pelo menos duas datas para acompanhar a evolução.")
        else {
            val max = complete.maxOf { it.cents }.coerceAtLeast(1L)
            val firstDay = complete.first().date.toEpochDay()
            val days = (complete.last().date.toEpochDay() - firstDay).coerceAtLeast(1L)
            ChartNote("Último registro completo")
            Text(ledgerMoney(complete.last().cents), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Escala: ${ledgerMoney(0)} a ${ledgerMoney(max)}", style = MaterialTheme.typography.labelSmall)
            Canvas(Modifier.fillMaxWidth().height(140.dp).padding(8.dp).semantics {
                contentDescription = complete.joinToString("; ") { "${AppDates.date(it.date)}: ${ledgerMoney(it.cents)}" }
            }) {
                val path = Path()
                complete.forEachIndexed { index, point ->
                    val x = (point.date.toEpochDay() - firstDay).toFloat() / days * size.width
                    val y = size.height * (1f - (point.cents.toDouble() / max).toFloat())
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, size.height), Offset(size.width, size.height))
                drawPath(path, NeonPalette[0], style = Stroke(2.dp.toPx()))
                complete.forEach { point ->
                    drawCircle(NeonPalette[0], 3.dp.toPx(), Offset(
                        (point.date.toEpochDay() - firstDay).toFloat() / days * size.width,
                        size.height * (1f - (point.cents.toDouble() / max).toFloat())))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(AppDates.date(complete.first().date), style = MaterialTheme.typography.labelSmall)
                Text(AppDates.date(complete.last().date), style = MaterialTheme.typography.labelSmall)
            }
        }
        ChartNote("Valores do histórico, sem estimar rendimentos entre registros. Inclui aportes e retiradas; não representa rentabilidade. Lançamentos retroativos corrigem o último ponto.")
        if (points.any { it.partial }) ChartNote("Datas com total parcial foram omitidas.")
    }
    GlassPanel {
        Text("Aportes e retiradas", style = MaterialTheme.typography.titleMedium)
        ChartNote("Últimos 6 meses · valores movimentados. Aportes incluem custos; retiradas incluem resgates, saques e vendas.")
        if (flows.all { it.contributions == 0L && it.withdrawals == 0L }) {
            Text("Nenhum aporte ou retirada registrado neste período.")
        } else {
            val max = flows.maxOf { maxOf(it.contributions, it.withdrawals) }.coerceAtLeast(1L)
            val monthFormat = DateTimeFormatter.ofPattern("MMM/uuuu", Locale.forLanguageTag("pt-BR"))
            flows.forEach { flow ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(flow.month.format(monthFormat), style = MaterialTheme.typography.labelLarge)
                    FlowBar("Aportes", flow.contributions, max, NeonPalette[0])
                    FlowBar("Retiradas", flow.withdrawals, max, Color(0xFFB790FF))
                }
            }
        }
    }
}

@Composable
private fun ChartNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FlowBar(label: String, cents: Long, max: Long, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(ledgerMoney(cents), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawLine(color.copy(alpha = 0.1f), Offset.Zero, Offset(size.width, 0f), strokeWidth = size.height)
            if (cents > 0) drawLine(color, Offset.Zero,
                Offset(size.width * (cents.toDouble() / max).toFloat(), 0f), strokeWidth = size.height)
        }
    }
}
