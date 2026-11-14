package br.com.yurimedeiros.meufinanceiro.assets

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AssetPerformanceTest {
    private val day = LocalDate.of(2026, 12, 8)
    private fun book(vararg changes: AssetChange): AssetBook = changes.fold(AssetBook(), AssetRules::apply)
    private fun register(id: String = "a", kind: AssetKind = AssetKind.INVESTMENT, cents: Long = 100000) =
        AssetChange.Register(AssetPosition(id, id, kind, cents, if (kind == AssetKind.BITCOIN) 100000000 else 0, day))
    private fun percent(book: AssetBook) = AssetPerformance.evolution(book, ZoneOffset.UTC).last().byAsset["a"]!!
    @Test fun equalRelativeChangesCompareRegardlessOfInitialAmount() {
        val history = book(register(cents = 100000), register("b", cents = 10000000),
            AssetChange.Confirm("a", 110000, 0, day.plusDays(1)), AssetChange.Confirm("b", 11000000, 0, day.plusDays(1)))
        val points = AssetPerformance.evolution(history)
        assertEquals(0.0, points.first().byAsset["a"]!!, 0.000001)
        assertEquals(10.0, points.last().byAsset["a"]!!, 0.000001)
        assertEquals(10.0, points.last().byAsset["b"]!!, 0.000001)
        assertEquals(10.0, points.last().byKind[AssetKind.INVESTMENT]!!, 0.000001)
    }
    @Test fun recordedFlowsAreNeutralAndLaterValuationsUseTheNewCapital() {
        val history = book(register(), AssetChange.Confirm("a", 110000, 0, day.plusDays(1)),
            AssetChange.Transfer("a", 1, AssetTransferKind.CONTRIBUTION, 50000, 0, 0, day.plusDays(2), false),
            AssetChange.Confirm("a", 176000, 0, day.plusDays(3)),
            AssetChange.Transfer("a", 2, AssetTransferKind.REDEMPTION, 10000, 0, 0, day.plusDays(4), false))
        assertEquals(listOf(0.0, 10.0, 10.0, 21.0, 21.0), AssetPerformance.evolution(history).map { it.byAsset["a"] })
    }
    @Test fun registrationsAndRemovalsDoNotCreateReturnsForTheType() {
        val history = book(register(kind = AssetKind.VEHICLE),
            AssetChange.Quote("a", 110000, null, "2026-12-09T12:00:00Z", "FIPE"),
            register("b", AssetKind.VEHICLE, 9000000), AssetChange.Remove("b", day.plusDays(2)),
            AssetChange.Remove("a", day.plusDays(3)), register("c", AssetKind.FGTS, 1000))
        assertEquals(10.0, AssetPerformance.evolution(history).last().byKind[AssetKind.VEHICLE]!!, 0.000001)
    }
    @Test fun bitcoinQuantityChangesAreNeutralAndMissingQuotesHaveNoInventedPercentage() {
        val history = book(register(kind = AssetKind.BITCOIN),
            AssetChange.Quote("a", 0, "300000", "2026-12-09T12:00:00Z", "BTC/BRL"),
            AssetChange.Transfer("a", 1, AssetTransferKind.CONTRIBUTION, 30000000, 0, 100000000, day.plusDays(2), false),
            AssetChange.Quote("a", 0, "330000", "2026-12-11T12:00:00Z", "BTC/BRL"))
        val points = AssetPerformance.evolution(history)
        assertNull(points.first().byAsset["a"])
        assertEquals(0.0, points[1].byAsset["a"]!!, 0.000001)
        assertEquals(0.0, points[2].byAsset["a"]!!, 0.000001)
        assertEquals(10.0, points.last().byAsset["a"]!!, 0.000001)
    }
    @Test fun zeroBaseReopensWithoutDivisionByZeroAndLossesCanReachMinusOneHundred() {
        assertEquals(0.0, percent(book(register(cents = 0), AssetChange.Confirm("a", 1000, 0, day.plusDays(1)))), 0.000001)
        assertEquals(-100.0, percent(book(register(), AssetChange.Confirm("a", 0, 0, day.plusDays(1)))), 0.000001)
        assertEquals(-10.0, percent(book(register(), AssetChange.Confirm("a", 90000, 0, day.plusDays(1)))), 0.000001)
    }
    @Test fun withdrawingAllThenAddingCapitalPreservesTheAccumulatedIndex() {
        val history = book(register(), AssetChange.Confirm("a", 110000, 0, day.plusDays(1)),
            AssetChange.Transfer("a", 1, AssetTransferKind.REDEMPTION, 110000, 0, 0, day.plusDays(2), false),
            AssetChange.Transfer("a", 2, AssetTransferKind.CONTRIBUTION, 200000, 0, 0, day.plusDays(3), false),
            AssetChange.Confirm("a", 220000, 0, day.plusDays(4)))
        assertEquals(listOf(0.0, 10.0, 10.0, 10.0, 21.0), AssetPerformance.evolution(history).map { it.byAsset["a"] })
    }
    @Test fun backdatedIncludedFlowCorrectsLatestPointWithoutChangingPerformanceOrHistory() {
        val history = book(register(), AssetChange.Confirm("a", 110000, 0, day.plusDays(2)),
            AssetChange.Transfer("a", 1, AssetTransferKind.CONTRIBUTION, 10000, 0, 0, day.minusDays(1), true))
        val saved = history.history.toList()
        val points = AssetPerformance.evolution(history)
        assertEquals(listOf(day, day.plusDays(2)), points.map { it.date })
        assertEquals(10.0, points.last().byAsset["a"]!!, 0.000001)
        assertEquals(saved, history.history)
        assertTrue(AssetPerformance.evolution(AssetBook()).isEmpty())
    }
}
