package br.com.yurimedeiros.meufinanceiro.assets

import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class AssetPerformancePoint(val date: LocalDate, val byKind: Map<AssetKind, Double?>, val byAsset: Map<String, Double?>)

/** Linked valuation changes, neutral to recorded flows. Not net investment return. */
internal object AssetPerformance {
    fun evolution(book: AssetBook, zone: ZoneId = ZoneId.systemDefault()): List<AssetPerformancePoint> {
        var replay = AssetBook()
        var reference: LocalDate? = null
        val byAsset = linkedMapOf<String, BigDecimal?>()
        val byKind = linkedMapOf<AssetKind, BigDecimal?>()
        val points = linkedMapOf<LocalDate, AssetPerformancePoint>()
        fun totals(assets: List<AssetPosition>) = assets.groupBy { it.kind }.mapValues { (_, rows) ->
            if (rows.any { it.valueCents == null }) null else rows.fold(0L) { total, row -> Math.addExact(total, row.valueCents ?: 0) }
        }
        fun <K> update(factors: MutableMap<K, BigDecimal?>, key: K, before: Long?, after: Long?, valuation: Boolean) {
            val previous = factors[key]
            factors[key] = when {
                after == null -> null
                previous == null || before == null -> BigDecimal.ONE
                valuation && before > 0 -> previous.multiply(BigDecimal.valueOf(after).divide(BigDecimal.valueOf(before), MathContext.DECIMAL128), MathContext.DECIMAL128)
                else -> previous
            }
        }
        fun <K> percentages(factors: Map<K, BigDecimal?>): Map<K, Double?> = factors.mapValues { (_, factor) ->
            factor?.subtract(BigDecimal.ONE)?.multiply(BigDecimal("100"))?.toDouble()?.takeIf { it.isFinite() }
        }
        book.history.forEach { change ->
            val previous = replay.assets.associateBy { it.id }
            val previousTotals = totals(replay.assets)
            replay = AssetRules.apply(replay.copy(history = emptyList()), change)
            val valuedId = when (change) {
                is AssetChange.Quote -> change.id
                is AssetChange.Confirm -> change.id.takeUnless { previous[it]?.kind == AssetKind.BITCOIN }
                else -> null
            }
            replay.assets.forEach { asset ->
                update(byAsset, asset.id, previous[asset.id]?.valueCents, asset.valueCents, asset.id == valuedId)
            }
            totals(replay.assets).forEach { (kind, value) ->
                update(byKind, kind, previousTotals[kind], value, previous[valuedId]?.kind == kind)
            }
            val date = when (change) {
                is AssetChange.Register -> change.asset.confirmedDate
                is AssetChange.Confirm -> change.date
                is AssetChange.Transfer -> change.date
                is AssetChange.Remove -> change.date
                is AssetChange.Quote -> Instant.parse(change.timestamp).atZone(zone).toLocalDate()
                else -> null
            }
            if (date != null) {
                val day = reference?.let { maxOf(it, date) } ?: date
                reference = day
                points[day] = AssetPerformancePoint(day, percentages(byKind), percentages(byAsset))
            }
        }
        return points.values.toList()
    }
}
