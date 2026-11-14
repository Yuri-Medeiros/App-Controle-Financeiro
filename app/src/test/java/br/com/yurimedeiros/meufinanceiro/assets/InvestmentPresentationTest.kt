package br.com.yurimedeiros.meufinanceiro.assets

import br.com.yurimedeiros.meufinanceiro.importing.CustodyStatements
import br.com.yurimedeiros.meufinanceiro.ledger.FinanceLedger
import br.com.yurimedeiros.meufinanceiro.ledger.LedgerAction
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InvestmentPresentationTest {
    private fun asset(name: String) = AssetPosition("manual", name, AssetKind.INVESTMENT, 10000, 0, LocalDate.of(2026, 11, 2))
    private fun statement() = CustodyStatements.parse(checkNotNull(javaClass.getResource("/nubank-custody-tables.txt")).readText())

    @Test fun exchangeLabelsUseTickerWithoutRenamingStoredPositions() {
        for ((name, label) in listOf("Trend IFIX-L (XFIX11)" to "XFIX11", "Trend LBMA Ouro (GOLD11)" to "GOLD11",
            "Ishares Ibovespa (BOVA11)" to "BOVA11", "Petrobras (PETR4)" to "PETR4", "B3 (B3SA3)" to "B3SA3",
            "Caixinha Turbo" to "Caixinha Turbo", "Tesouro IPCA+ 2032" to "Tesouro IPCA+ 2032")) {
            val position = asset(name)
            assertEquals(label, InvestmentPresentation.label(position))
            assertEquals(name, position.name)
            assertEquals("manual", position.id)
        }
        assertEquals("Meu carro (ABCD12)", InvestmentPresentation.label(asset("Meu carro (ABCD12)").copy(kind = AssetKind.VEHICLE)))
    }

    @Test fun distributionUsesDocumentTypesAndSupportsLegacyImportIdentitiesWithoutGuessingManualTypes() {
        val document = statement()
        val book = FinanceLedger.replay(CustodyStatements.actions(document, AssetBook())).assets
        val expected = mapOf(InvestmentType.RDB to 2121605L, InvestmentType.TREASURY to 948033L, InvestmentType.ETF to 530130L)
        assertEquals(expected, InvestmentPresentation.totalsByType(book.assets))
        assertEquals(document.net, expected.values.sum())
        assertEquals(expected, InvestmentPresentation.totalsByType(book.assets.map { it.copy(investmentType = null) }))
        assertEquals(InvestmentType.OTHER, InvestmentPresentation.type(asset("Trend IFIX-L (XFIX11)")))
        assertEquals(InvestmentType.STOCK, InvestmentPresentation.type(asset("PETR4").copy(investmentType = InvestmentType.STOCK)))
        assertTrue(InvestmentPresentation.totalsByType(book.assets.map { it.copy(disposed = true) }).isEmpty())
    }

    @Test fun importBackfillsOnlyMissingTypesAndKeepsAmountsNamesAndIdsStable() {
        val document = statement()
        val actions = CustodyStatements.actions(document, AssetBook())
        val legacyActions = actions.map { action ->
            val registration = (action as LedgerAction.Asset).change as AssetChange.Register
            val json = AssetCodec.encode(registration).apply { getJSONObject("asset").remove("investmentType") }
            LedgerAction.Asset(AssetCodec.decode(json))
        }
        val legacy = FinanceLedger.replay(legacyActions).assets
        assertTrue(legacy.assets.all { it.investmentType == null })
        val fill = CustodyStatements.actions(document, legacy)
        assertEquals(6, fill.size)
        fill.forEach { action ->
            val change = (action as LedgerAction.Asset).change
            assertEquals(change, AssetCodec.decode(AssetCodec.encode(change)))
        }
        val updated = FinanceLedger.replay(legacyActions + fill).assets
        assertEquals(legacy.assets.map { Triple(it.id, it.name, it.cents) }, updated.assets.map { Triple(it.id, it.name, it.cents) })
        assertTrue(updated.assets.all { it.investmentType != null })
        assertTrue(CustodyStatements.actions(document, updated).isEmpty())
        val position = updated.assets.last()
        val oldConfirmation = AssetCodec.encode(AssetChange.Confirm(position.id, position.cents, 0, document.date))
        assertEquals(position.investmentType, AssetRules.apply(updated, AssetCodec.decode(oldConfirmation)).assets.last().investmentType)
        actions.forEach { action ->
            val change = (action as LedgerAction.Asset).change
            assertEquals(change, AssetCodec.decode(AssetCodec.encode(change)))
        }
    }
}
