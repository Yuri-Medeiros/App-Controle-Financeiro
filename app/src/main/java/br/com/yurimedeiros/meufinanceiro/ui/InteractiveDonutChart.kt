package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot

internal data class DonutSlice(val key: String, val label: String, val cents: Long, val color: Color)

/** Coordinates match the ring drawn below, starting at twelve o'clock clockwise. */
internal fun donutSliceAt(values: List<Long>, x: Float, y: Float, width: Float, height: Float): Int? {
    if (!x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite()) return null
    val diameter = minOf(width, height)
    if (diameter <= 0f) return null
    val dx = x - width / 2f; val dy = y - height / 2f
    val radius = hypot(dx, dy)
    if (radius < diameter / 2f - diameter * 0.16f || radius > diameter / 2f) return null
    val total = values.filter { it > 0 }.sumOf { it.toDouble() }
    if (total == 0.0) return null
    val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 450.0) % 360.0
    var end = 0.0
    values.forEachIndexed { index, cents ->
        if (cents > 0) {
            end += cents.toDouble() / total * 360.0
            if (angle < end) return index
        }
    }
    return values.indexOfLast { it > 0 }.takeIf { it >= 0 }
}

@Composable
internal fun InteractiveDonutChart(title: String, slices: List<DonutSlice>, modifier: Modifier = Modifier) {
    var selectedKey by remember(slices.map { it.key }) { mutableStateOf<String?>(null) }
    val selected = slices.firstOrNull { it.key == selectedKey }
    val total = slices.sumOf { it.cents }
    fun details(slice: DonutSlice) = ledgerMoney(slice.cents) + " · " +
        String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", slice.cents.toDouble() / total * 100)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(Modifier.fillMaxSize().padding(8.dp).pointerInput(slices) {
                detectTapGestures { point ->
                    selectedKey = donutSliceAt(slices.map { it.cents }, point.x, point.y, size.width.toFloat(), size.height.toFloat())
                        ?.let { slices[it].key }
                }
            }.semantics {
                contentDescription = title + ": " + slices.joinToString("; ") { it.label + " " + details(it) }
                customActions = slices.map { slice -> CustomAccessibilityAction("Selecionar ${slice.label}") {
                    selectedKey = slice.key; true
                } }
            }) {
                val diameter = size.minDimension
                val stroke = diameter * 0.16f
                val corner = Offset((size.width - diameter + stroke) / 2f, (size.height - diameter + stroke) / 2f)
                var start = -90f
                slices.forEach { slice ->
                    val sweep = (slice.cents.toDouble() / total * 360).toFloat()
                    drawArc(slice.color.copy(alpha = if (selected == null || selected.key == slice.key) 1f else 0.35f),
                        start, sweep, false, corner, Size(diameter - stroke, diameter - stroke), style = Stroke(stroke))
                    start += sweep
                }
            }
            DropdownMenu(expanded = selected != null, onDismissRequest = { selectedKey = null }, modifier = Modifier.widthIn(max = 320.dp)) {
                selected?.let { slice ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(slice.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(details(slice), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
