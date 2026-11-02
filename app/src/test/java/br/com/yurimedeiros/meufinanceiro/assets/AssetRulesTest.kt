package br.com.yurimedeiros.meufinanceiro.assets

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AssetRulesTest {
    private val date = LocalDate.of(2026, 10, 30)
    private fun asset(kind: AssetKind = AssetKind.INVESTMENT, cents: Long = 100000, sats: Long = 0) = AssetPosition("a", "Ativo", kind, cents, sats, date)
    @Test fun contributionsAndRedemptionsApplyActualNetAndFeesExactlyOnce() {
        var book = AssetRules.apply(AssetBook(), AssetChange.Register(asset()))
        book = AssetRules.apply(book, AssetChange.Transfer("a", 1, AssetTransferKind.CONTRIBUTION, 20000, 1000, 0, date, false))
        assertEquals(119000L, book.assets.single().cents)
        book = AssetRules.apply(book, AssetChange.Transfer("a", 2, AssetTransferKind.REDEMPTION, 10000, 1000, 0, date, false))
        assertEquals(108000L, book.assets.single().cents)
        assertTrue(runCatching { AssetRules.apply(book, AssetChange.Transfer("a", 2, AssetTransferKind.REDEMPTION, 10000, 1000, 0, date, false)) }.isFailure)
    }
    @Test fun linkingAnOperationAlreadyIncludedInConfirmedBalanceDoesNotDuplicateHoldings() {
        val book = AssetRules.apply(AssetBook(), AssetChange.Register(asset()))
        val linked = AssetRules.apply(book, AssetChange.Transfer("a", 1, AssetTransferKind.CONTRIBUTION, 20000, 0, 0, date.minusDays(2), true))
        assertEquals(100000L, linked.assets.single().cents)
    }
    @Test fun vehicleSaleRemovesValuationButPreservesHistoryAndDoesNotCreateBankIncomeTwice() {
        var book = AssetRules.apply(AssetBook(), AssetChange.Register(asset(AssetKind.VEHICLE, 5000000)))
        book = AssetRules.apply(book, AssetChange.Transfer("a", 1, AssetTransferKind.SALE, 4900000, 100000, 0, date, false))
        assertEquals(0L, book.assets.single().valueCents)
        assertEquals(5000000L, (book.history.first() as AssetChange.Register).asset.cents)
        assertEquals(4900000L, (book.history.last() as AssetChange.Transfer).bankCents)
    }
    @Test fun bitcoinUsesSatoshisAndNeverInfersQuantityFromSpotPrice() {
        assertEquals(1L, AssetRules.satoshis("0.00000001"))
        assertTrue(runCatching { AssetRules.satoshis("0.000000001") }.isFailure)
        var book = AssetRules.apply(AssetBook(), AssetChange.Register(asset(AssetKind.BITCOIN, 0, 10000000)))
        book = AssetRules.apply(book, AssetChange.Quote("a", 0, "500000.00", "2026-10-30T12:00:00Z", "Reference"))
        assertEquals(5000000L, book.assets.single().valueCents)
        book = AssetRules.apply(book, AssetChange.Transfer("a", 1, AssetTransferKind.REDEMPTION, 1000000, 0, 1000000, date, false))
        assertEquals(9000000L, book.assets.single().satoshis)
    }
    @Test fun realFgtsCorrectionPreservesPriorConfirmationAndConfiguration() {
        var book = AssetRules.apply(AssetBook(), AssetChange.Register(asset(AssetKind.FGTS)))
        book = AssetRules.apply(book, AssetChange.Configure("a", "", 50000, true))
        book = AssetRules.apply(book, AssetChange.Confirm("a", 160000, 0, date.plusMonths(1)))
        assertEquals(160000L, book.assets.single().cents)
        assertEquals(100000L, (book.history.first() as AssetChange.Register).asset.cents)
        assertEquals(50000L, book.assets.single().monthlyFgts)
    }
    @Test fun goalDefaultsExcludeFlashAndReceivablesAndNeverChangeBankBalances() {
        val book = AssetRules.apply(AssetBook(), AssetChange.Register(asset()))
        assertFalse(book.goal.includeBank)
        assertFalse(book.goal.includeFlash)
        assertFalse(book.goal.includeReceivables)
        assertEquals(setOf("a"), book.goal.selectedAssets)
    }
}
