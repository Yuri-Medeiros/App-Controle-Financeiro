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
        assertEquals(0L, points.last().byKind[AssetKind.VEHICLE])
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
        assertEquals(100000L, points.first().byKind[AssetKind.INVESTMENT])
        assertNull(points.first().byKind[AssetKind.BITCOIN])
        assertFalse(points.last().partial)
        assertEquals(30100000L, points.last().cents)
        assertEquals(30000000L, points.last().byKind[AssetKind.BITCOIN])
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

    @Test fun typeTotalsCombinePositionsAndRemainVisibleWhenAnotherTypeHasNoQuote() {
        var book = AssetRules.apply(AssetBook(), register())
        book = AssetRules.apply(book, register("b"))
        book = AssetRules.apply(book, register("btc", AssetKind.BITCOIN))
        book = AssetRules.apply(book, AssetChange.Confirm("b", 150000, 0, day.plusDays(1)))
        val points = AssetInsights.evolution(book)
        assertEquals(listOf(200000L, 250000L), points.map { it.byKind[AssetKind.INVESTMENT] })
        assertTrue(points.all { it.partial && it.byKind[AssetKind.BITCOIN] == null })
    }

    @Test fun emptyHistoryHasNoInventedEvolutionOrFlows() {
        assertTrue(AssetInsights.evolution(AssetBook()).isEmpty())
        assertTrue(AssetInsights.flows(AssetBook(), day).all { it.contributions == 0L && it.withdrawals == 0L })
    }
    @Test fun investmentProjectionExcludesOtherTypesAndTheirUpdateDatesWithoutChangingTheBook() {
        var book = AssetRules.apply(AssetBook(), register("investment"))
        book = AssetRules.apply(book, register("btc", AssetKind.BITCOIN))
        book = AssetRules.apply(book, AssetChange.Quote("btc", 0, "300000", "2026-11-09T12:00:00Z", "test"))
        book = AssetRules.apply(book, AssetChange.Confirm("investment", 120000, 0, day.plusDays(1)))
        book = AssetRules.apply(book, AssetChange.Goal(GoalSettings(selectedAssets = setOf("investment", "btc"))))
        val projected = AssetInsights.forKind(book, AssetKind.INVESTMENT)
        assertEquals(listOf("investment"), projected.assets.map { it.id })
        assertEquals(2, projected.history.size)
        assertTrue(projected.history.none { it is AssetChange.Goal })
        val points = AssetInsights.evolution(projected, ZoneOffset.UTC)
        assertEquals(listOf(day, day.plusDays(1)), points.map { it.date })
        assertEquals(listOf(100000L, 120000L), points.map { it.cents })
        assertTrue(points.all { it.byAsset.keys == setOf("investment") && !it.partial })
        assertEquals(2, book.assets.size)
        assertEquals(5, book.history.size)
    }

    @Test fun perAssetSeriesKeepSeparateIdentitiesAndDoNotInventValuesBeforeRegistration() {
        var book = AssetRules.apply(AssetBook(), register("a"))
        book = AssetRules.apply(book, register("b").let { it.copy(asset = it.asset.copy(name = "a", cents = 200000, confirmedDate = day.plusDays(1))) })
        book = AssetRules.apply(book, AssetChange.Confirm("a", 150000, 0, day.plusDays(2)))
        val points = AssetInsights.evolution(book)
        assertFalse(points.first().byAsset.containsKey("b"))
        assertEquals(mapOf("a" to 100000L, "b" to 200000L), points[1].byAsset)
        assertEquals(mapOf("a" to 150000L, "b" to 200000L), points.last().byAsset)
        assertEquals(350000L, points.last().cents)
        assertTrue(AssetInsights.evolution(AssetInsights.forKind(book, AssetKind.VEHICLE)).isEmpty())
    }

    @Test fun importedCustodyProducesSixIndividualInvestmentSeries() {
        val text = checkNotNull(javaClass.getResource("/nubank-custody-tables.txt")).readText()
        val statement = br.com.yurimedeiros.meufinanceiro.importing.CustodyStatements.parse(text)
        val actions = br.com.yurimedeiros.meufinanceiro.importing.CustodyStatements.actions(statement, AssetBook())
        val book = br.com.yurimedeiros.meufinanceiro.ledger.FinanceLedger.replay(actions).assets
        val investments = AssetInsights.forKind(book, AssetKind.INVESTMENT)
        val point = AssetInsights.evolution(investments).single()
        assertEquals(6, point.byAsset.size)
        assertEquals(3599768L, point.cents)
        assertEquals(investments.assets.associate { it.id to it.cents }, point.byAsset)
        assertEquals(statement.date, point.date)
    }

}
