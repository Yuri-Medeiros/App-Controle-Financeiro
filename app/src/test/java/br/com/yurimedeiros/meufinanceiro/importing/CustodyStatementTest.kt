package br.com.yurimedeiros.meufinanceiro.importing

import br.com.yurimedeiros.meufinanceiro.ledger.*
import org.junit.Assert.*
import org.junit.Test

class CustodyStatementTest {
    private val text = checkNotNull(javaClass.getResource("/nubank-custody-tables.txt")).readText()
    @Test fun readsEachPositionAndReconcilesGrossAndNetIncludingB3() {
        val statement = CustodyStatements.parse(text)
        assertEquals(6, statement.positions.size)
        assertEquals(3645024L, statement.gross)
        assertEquals(3599768L, statement.net)
        assertEquals(listOf(1103886L, 1017719L, 948033L, 128800L, 153230L, 248100L), statement.positions.map { it.net })
        assertTrue(statement.positions[2].description.contains("13,43"))
        assertEquals("Trend IFIX-L (XFIX11)", statement.positions[3].name)
    }
    @Test fun handlesWhitespaceAndRejectsPartialOrAlteredTables() {
        assertEquals(6, CustodyStatements.parse(text.replace('\n', ' ')).positions.size)
        assertTrue(runCatching { CustodyStatements.parse(text.replace("11.038,86", "11.038,85")) }.isFailure)
        assertTrue(runCatching { CustodyStatements.parse(text.substringBefore("Custódia em Bolsa")) }.isFailure)
        assertTrue(runCatching { CustodyStatements.parse("not a statement") }.isFailure)
    }
    @Test fun repeatImportDoesNotDuplicateAndOlderStatementCannotOverwrite() {
        val statement = CustodyStatements.parse(text)
        val actions = CustodyStatements.actions(statement, br.com.yurimedeiros.meufinanceiro.assets.AssetBook())
        val book = FinanceLedger.replay(actions).assets
        assertTrue(CustodyStatements.actions(statement, book).isEmpty())
        assertTrue(runCatching { CustodyStatements.actions(statement.copy(date = statement.date.minusDays(1)), book) }.isFailure)
        val changed = statement.copy(date = statement.date.plusDays(1))
        val next = FinanceLedger.replay(actions + CustodyStatements.actions(changed, book))
        assertEquals(6, next.assets.assets.size)
        assertEquals(3599768L, next.assets.assets.sumOf { it.cents })
    }
}
