package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import br.com.yurimedeiros.meufinanceiro.assets.AssetHistoryPoint
import br.com.yurimedeiros.meufinanceiro.assets.AssetKind
import br.com.yurimedeiros.meufinanceiro.formatting.AppDates

// Returned centers preserve the vertical ordering of the endpoints, with enough
// room for every two-line label, including large accessibility fonts.
internal fun evolutionLabelPositions(desired: List<Float>, labelHeight: Float, height: Float, gap: Float): List<Float> {
    if (desired.isEmpty()) return emptyList()
    val order = desired.indices.sortedBy { desired[it] }
    val centers = FloatArray(desired.size)
    val half = labelHeight / 2f
    val distance = labelHeight + gap
    order.forEachIndexed { index, item ->
        centers[item] = maxOf(desired[item].coerceIn(half, height - half),
            if (index == 0) half else centers[order[index - 1]] + distance)
    }
    centers[order.last()] = minOf(centers[order.last()], height - half)
    for (index in order.lastIndex - 1 downTo 0) {
        centers[order[index]] = minOf(centers[order[index]], centers[order[index + 1]] - distance)
    }
    return centers.toList()
}

internal data class EvolutionSeriesPoint(val date: LocalDate, val values: Map<String, Long?>)

@Composable
internal fun EvolutionLineChart(history: List<AssetHistoryPoint>, colors: Map<AssetKind, Color>, names: Map<AssetKind, String>) {
    EvolutionSeriesChart(history.map { point -> EvolutionSeriesPoint(point.date, point.byKind.mapKeys { it.key.name }) },
        colors.mapKeys { it.key.name }, names.mapKeys { it.key.name })
}

@Composable
internal fun EvolutionSeriesChart(history: List<EvolutionSeriesPoint>, colors: Map<String, Color>, names: Map<String, String>) {
    val points = history.takeLast(8)
    val kinds = names.keys.filter { kind -> points.any { it.values[kind] != null } }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelHeightDp = with(androidx.compose.ui.platform.LocalDensity.current) {
        measurer.measure("Investimento\nNome\nR$ 100.000,00", style).size.height.toDp()
    }
    val chartHeight = maxOf(200.dp, (labelHeightDp + 8.dp) * kinds.size + 16.dp)
    val axisHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        measurer.measure("00/00/0000", style).size.height.toDp()
    } + 12.dp
    val description = if (points.isEmpty()) "Evolução: sem atualizações registradas" else points.joinToString("; ") { point ->
        AppDates.date(point.date) + ": " + kinds.joinToString(", ") { kind ->
            names.getValue(kind) + " " + (point.values[kind]?.let(::ledgerMoney) ?: "sem valor disponível")
        }
    }
    Canvas(Modifier.fillMaxWidth().height(chartHeight + axisHeight).semantics { contentDescription = description }) {
        val top = 8.dp.toPx()
        val bottom = size.height - axisHeight.toPx()
        val height = bottom - top
        val labels = kinds.map { kind ->
            val last = points.last().values[kind]
            measurer.measure(names.getValue(kind).let { if (it.length > 32) it.take(29) + "…" else it } + "\n" + (last?.let(::ledgerMoney) ?: "Sem cotação"), style,
                softWrap = true, maxLines = 3, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = (size.width * 0.43f).toInt().coerceAtLeast(1)))
        }
        val labelWidth = labels.maxOfOrNull { it.size.width }?.toFloat() ?: 0f
        val labelHeight = labels.maxOfOrNull { it.size.height }?.toFloat() ?: 0f
        val left = 4.dp.toPx()
        val right = (size.width - labelWidth - 16.dp.toPx()).coerceAtLeast(left + 32.dp.toPx())
        val max = points.flatMap { it.values.values }.filterNotNull().maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val firstDay = points.firstOrNull()?.date?.toEpochDay() ?: 0L
        val days = ((points.lastOrNull()?.date?.toEpochDay() ?: firstDay) - firstDay).coerceAtLeast(1L)
        fun x(point: EvolutionSeriesPoint) = if (points.size == 1) right else
            left + (point.date.toEpochDay() - firstDay).toFloat() / days * (right - left)
        fun y(cents: Long) = top + height * (1f - (cents.toDouble() / max).toFloat())
        repeat(3) { tick ->
            val at = top + height * tick / 2f
            drawLine(textColor.copy(alpha = 0.1f), Offset(left, at), Offset(right, at))
        }
        if (kinds.isEmpty()) {
            val empty = measurer.measure("Sem atualizações", style)
            drawText(empty, textColor, Offset((size.width - empty.size.width) / 2f, height / 2f))
        } else {
            val desired = kinds.map { kind -> y(points.last { it.values[kind] != null }.values.getValue(kind)!!) - top }
            val centers = evolutionLabelPositions(desired, labelHeight, height, 8.dp.toPx())
            kinds.forEachIndexed { index, kind ->
                val color = colors[kind] ?: NeonPalette[index % NeonPalette.size]
                val path = Path()
                var connected = false
                points.forEach { point ->
                    val cents = point.values[kind]
                    if (cents == null) connected = false else {
                        val at = Offset(x(point), y(cents))
                        if (connected) path.lineTo(at.x, at.y) else path.moveTo(at.x, at.y)
                        connected = true
                        drawCircle(color, 2.5.dp.toPx(), at)
                    }
                }
                drawPath(path, color, style = Stroke(2.dp.toPx()))
                val lastPoint = points.last { it.values[kind] != null }
                val endpoint = Offset(x(lastPoint), y(lastPoint.values.getValue(kind)!!))
                val center = top + centers[index]
                // A broken series ends at its last known point; no fabricated price.
                if (lastPoint == points.last()) {
                    drawLine(color.copy(alpha = 0.7f), endpoint, Offset(right + 8.dp.toPx(), center))
                }
                drawText(labels[index], textColor, Offset(right + 12.dp.toPx(), center - labels[index].size.height / 2f))
            }
        }
        // Keep update dates at their true x positions; omit crowded intermediate
        // labels while preserving the first and most recent date.
        val dates = points.map { point -> point to measurer.measure(AppDates.date(point.date), style) }
        if (dates.isNotEmpty()) {
            val last = dates.last()
            val lastX = (x(last.first) - last.second.size.width / 2f).coerceIn(0f, (right - last.second.size.width).coerceAtLeast(0f))
            var edge = -8.dp.toPx()
            dates.dropLast(1).forEach { (point, text) ->
                val at = (x(point) - text.size.width / 2f).coerceAtLeast(0f)
                if (at >= edge + 8.dp.toPx() && at + text.size.width + 8.dp.toPx() <= lastX) {
                    drawText(text, textColor, Offset(at, bottom + 8.dp.toPx()))
                    edge = at + text.size.width
                }
            }
            drawText(last.second, textColor, Offset(lastX, bottom + 8.dp.toPx()))
        }
    }
}
