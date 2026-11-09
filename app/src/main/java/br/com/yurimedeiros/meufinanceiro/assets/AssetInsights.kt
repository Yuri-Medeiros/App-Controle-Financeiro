package br.com.yurimedeiros.meufinanceiro.assets

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal data class AssetHistoryPoint(val date: LocalDate, val cents: Long, val partial: Boolean,
    val byKind: Map<AssetKind, Long?> = emptyMap())
internal data class AssetMonthFlow(val month: YearMonth, val contributions: Long, val withdrawals: Long)

internal object AssetInsights {
    // Replay journal order: confirmations can supersede earlier balances. A backdated
    // entry corrects the latest reference point rather than rewriting older snapshots.
    fun evolution(book: AssetBook, zone: ZoneId = ZoneId.systemDefault()): List<AssetHistoryPoint> {
        var replay = AssetBook()
        var reference: LocalDate? = null
        val points = linkedMapOf<LocalDate, AssetHistoryPoint>()
        book.history.forEach { change ->
            replay = AssetRules.apply(replay.copy(history = emptyList()), change)
            val date = when (change) {
                is AssetChange.Register -> change.asset.confirmedDate
                is AssetChange.Confirm -> change.date
                is AssetChange.Transfer -> change.date
                is AssetChange.Quote -> Instant.parse(change.timestamp).atZone(zone).toLocalDate()
                else -> null
            }
            if (date != null) {
                val day = reference?.let { maxOf(it, date) } ?: date
                reference = day
                val live = replay.assets.filter { !it.disposed }
                val byKind = replay.assets.groupBy { it.kind }.mapValues { (_, assets) ->
                    if (assets.any { it.valueCents == null }) null else assets.sumOf { it.valueCents ?: 0L }
                }
                points[day] = AssetHistoryPoint(day, live.mapNotNull { it.valueCents }.sum(), live.any { it.valueCents == null }, byKind)
            }
        }
        return points.values.toList()
    }

    fun flows(book: AssetBook, today: LocalDate): List<AssetMonthFlow> {
        val transfers = book.history.filterIsInstance<AssetChange.Transfer>()
        val end = YearMonth.from(today)
        return (5L downTo 0L).map { offset ->
            val month = end.minusMonths(offset)
            val rows = transfers.filter { YearMonth.from(it.date) == month }
            AssetMonthFlow(month,
                rows.filter { it.kind == AssetTransferKind.CONTRIBUTION }.sumOf { it.bankCents },
                rows.filter { it.kind != AssetTransferKind.CONTRIBUTION }.sumOf { it.bankCents })
        }
    }
}
