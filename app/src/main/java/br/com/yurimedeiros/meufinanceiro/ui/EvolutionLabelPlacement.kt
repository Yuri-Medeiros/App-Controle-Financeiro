package br.com.yurimedeiros.meufinanceiro.ui

internal data class EvolutionLabelTarget(val x: Float, val y: Float, val width: Float, val height: Float)
internal data class EvolutionLineSegment(val series: Int, val x1: Float, val y1: Float, val x2: Float, val y2: Float)
internal data class EvolutionLabelBox(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    fun overlaps(other: EvolutionLabelBox, gap: Float = 0f): Boolean =
        x < other.right + gap && right + gap > other.x && y < other.bottom + gap && bottom + gap > other.y
}

internal fun evolutionLineCrossesLabel(line: EvolutionLineSegment, box: EvolutionLabelBox): Boolean {
    var from = 0f; var to = 1f
    fun clip(p: Float, q: Float): Boolean {
        if (p == 0f) return q >= 0f
        val ratio = q / p
        if (p < 0f) from = maxOf(from, ratio) else to = minOf(to, ratio)
        return from <= to
    }
    val dx = line.x2 - line.x1; val dy = line.y2 - line.y1
    return clip(-dx, line.x1 - box.x) && clip(dx, box.right - line.x1) &&
        clip(-dy, line.y1 - box.y) && clip(dy, box.bottom - line.y1)
}

/** Prefer above the endpoint, then move backward along the plot before using spare rows. */
internal fun evolutionLabelBoxes(
    targets: List<EvolutionLabelTarget>, width: Float, height: Float, gap: Float,
    lines: List<EvolutionLineSegment> = emptyList(),
): List<EvolutionLabelBox> {
    if (targets.isEmpty()) return emptyList()
    val maxHeight = targets.maxOf { it.height }
    val placed = arrayOfNulls<EvolutionLabelBox>(targets.size)
    for (index in targets.indices.sortedBy { targets[it].y }) {
        val target = targets[index]
        val candidates = mutableListOf<EvolutionLabelBox>()
        fun add(x: Float, y: Float) {
            if (y >= 0f && y + target.height <= height) {
                candidates += EvolutionLabelBox(x.coerceIn(0f, width - target.width), y, target.width, target.height)
            }
        }
        val front = (target.x - target.width).coerceIn(0f, width - target.width)
        val behind = (target.x - target.width - gap).coerceIn(0f, width - target.width)
        val columns = mutableListOf(front)
        var x = front - target.width - gap
        while (x >= 0f) { columns += x; x -= target.width + gap }
        columns += 0f
        columns.distinct().forEach { add(it, target.y - target.height - gap) }
        (listOf(behind) + columns).distinct().forEach { add(it, target.y - target.height / 2f) }
        val rows = mutableListOf<Float>()
        var y = 0f
        while (y + target.height <= height) { rows += y; y += maxHeight + gap }
        rows.sortedBy { kotlin.math.abs(it - (target.y - target.height - gap)) }.forEach { row ->
            (listOf(behind) + columns).distinct().forEach { add(it, row) }
        }
        val free = candidates.distinct().filter { box -> placed.filterNotNull().none { box.overlaps(it, gap) } }
        fun crossings(box: EvolutionLabelBox): Int {
            val padded = EvolutionLabelBox(box.x - gap / 2, box.y - gap / 2, box.width + gap, box.height + gap)
            return lines.count { it.series != index && evolutionLineCrossesLabel(it, padded) }
        }
        val selected = free.firstOrNull { crossings(it) == 0 } ?: free.minByOrNull(::crossings)
        if (selected == null) {
            // A narrow chart or tightly clustered values may need a full reflow.
            val centers = evolutionLabelPositions(targets.map { it.y - gap - it.height / 2f }, maxHeight, height, gap)
            return targets.mapIndexed { i, item -> EvolutionLabelBox(
                (item.x - item.width - gap).coerceIn(0f, width - item.width),
                centers[i] - item.height / 2f, item.width, item.height)
            }
        }
        placed[index] = selected
    }
    return placed.map { checkNotNull(it) }
}
