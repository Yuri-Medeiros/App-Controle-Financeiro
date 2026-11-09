package br.com.yurimedeiros.meufinanceiro.assets

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AssetInsightsTest {
    private val day = LocalDate.of(2026, 11, 2)
    private fun register(id: String = "a", kind: AssetKind = AssetKind.INVESTMENT) =
        AssetChange.Register(AssetPosition(id, id, kind, 100000, if (kind == AssetKind.BITCOIN) 100000000 else 0, day))

    @Test fun sameDayBalancesCollapseAndSaleRemovesVehicle() {
        var book = AssetRules.apply(AssetBook(), register(kind = AssetKind.VEHICLE))
        book = AssetRules.apply(book, AssetChange.Transfer("a", 1, AssetTransferKind.CONTRIBUTION, 20000, 1000, 0, day, false))
        book = AssetRules.apply(book, AssetChange.Transfer("a", 2, AssetTransferKind.SALE, 110000, 1000, 0, day.plusDays(1), false))
        val points = AssetInsights.evolution(book)
        assertEquals(2, points.size)
        assertEquals(119000L, points.first().cents)
        assertEquals(0L, points.last().cents)
        val flows = AssetInsights.flows(book, day).last()
        assertEquals(20000L, flows.contributions)
        assertEquals(110000L, flows.withdrawals)
    }

    @Test fun missingBitcoinQuoteIsPartialAndLaterQuoteUsesActualQuantity() {
        var book = AssetRules.apply(AssetBook(), register())
        book = AssetRules.apply(book, register("btc", AssetKind.BITCOIN))
        book = AssetRules.apply(book, AssetChange.Quote("btc", 0, "300000", "2026-11-03T12:00:00Z", "test"))
        val points = AssetInsights.evolution(book, ZoneOffset.UTC)
        assertTrue(points.first().partial)
        assertFalse(points.last().partial)
        assertEquals(30100000L, points.last().cents)
    }

    @Test fun backdatedIncludedTransferCountsAsFlowWithoutDuplicatingHoldings() {
        var book = AssetRules.apply(AssetBook(), register())
        book = AssetRules.apply(book, AssetChange.Transfer("a", 1, AssetTransferKind.CONTRIBUTION, 20000, 0, 0, day.minusDays(4), true))
        val points = AssetInsights.evolution(book)
        assertEquals(1, points.size)
        assertEquals(day, points.single().date)
        assertEquals(100000L, points.single().cents)
        val flows = AssetInsights.flows(book, day)
        assertEquals(6, flows.size)
        assertEquals(20000L, flows[4].contributions)
        assertEquals(0L, flows.last().contributions)
    }

    @Test fun emptyHistoryHasNoInventedEvolutionOrFlows() {
        assertTrue(AssetInsights.evolution(AssetBook()).isEmpty())
        assertTrue(AssetInsights.flows(AssetBook(), day).all { it.contributions == 0L && it.withdrawals == 0L })
    }
}
