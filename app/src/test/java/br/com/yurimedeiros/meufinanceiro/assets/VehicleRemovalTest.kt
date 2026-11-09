package br.com.yurimedeiros.meufinanceiro.assets

import br.com.yurimedeiros.meufinanceiro.ledger.FinanceLedger
import br.com.yurimedeiros.meufinanceiro.ledger.LedgerAction
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VehicleRemovalTest {
    private val day = LocalDate.of(2026, 11, 9)
    private val vehicle = AssetPosition("moto", "Moto", AssetKind.VEHICLE, 1513700, 0, day.minusDays(7),
        vehiclePath = "motos/marcas/101/modelos/1234/anos/2020-1", fipeReference = "2026-11",
        quotedAt = "2026-11-09T12:00:00Z", quoteSource = "FIPE (Parallelum) · YAMAHA Modelo · 827088-0 · novembro de 2026")

    @Test fun removalPreservesHistoryAndValuationWithoutBankMovementOrSale() {
        val registration = AssetChange.Register(vehicle)
        val removal = AssetChange.Remove(vehicle.id, day)
        assertEquals(removal, AssetCodec.decode(AssetCodec.encode(removal)))
        val snapshot = FinanceLedger.replay(listOf(LedgerAction.Asset(registration), LedgerAction.Asset(removal)))
        val book = snapshot.assets
        val removed = book.assets.single()
        assertEquals(vehicle.copy(disposed = true), removed)
        assertEquals(0L, removed.valueCents)
        assertEquals(listOf(registration, removal), book.history)
        assertTrue(snapshot.usedMovementIds.isEmpty())
        assertTrue(book.history.filterIsInstance<AssetChange.Transfer>().isEmpty())
        assertFalse(FipeQuotes.due(removed, day, manual = true))
        val points = AssetInsights.evolution(book)
        assertEquals(1513700L, points.first().cents)
        assertEquals(day, points.last().date)
        assertEquals(0L, points.last().cents)
        assertEquals(0L, points.last().byKind[AssetKind.VEHICLE])
        assertTrue(AssetInsights.flows(book, day).all { it.contributions == 0L && it.withdrawals == 0L })
    }

    @Test fun removalRejectsDuplicateUnknownAndOtherAssetKinds() {
        val book = AssetRules.apply(AssetBook(), AssetChange.Register(vehicle))
        val removal = AssetChange.Remove(vehicle.id, day)
        assertTrue(runCatching { AssetRules.apply(AssetRules.apply(book, removal), removal) }.isFailure)
        assertTrue(runCatching { AssetRules.apply(book, removal.copy(id = "missing")) }.isFailure)
        val investment = book.copy(assets = listOf(vehicle.copy(kind = AssetKind.INVESTMENT)))
        assertTrue(runCatching { AssetRules.apply(investment, removal) }.isFailure)
    }
}
