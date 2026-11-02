package br.com.yurimedeiros.meufinanceiro.ui

import org.junit.Assert.*
import org.junit.Test

class ImportNotificationTest {
    @Test fun successfulImportReportsOnlyNewRows() {
        val result = ImportNotification(1, false, 89)
        assertEquals("Importação concluída", result.title)
        assertEquals("89 movimentações importadas.", result.message)
        assertFalse(result.hasWarnings)
    }

    @Test fun repeatedImportReportsNoNewRows() {
        assertEquals("Nenhuma nova movimentação importada.", ImportNotification(2, false, 0).message)
    }

    @Test fun partialFailureKeepsTheCommittedCount() {
        val result = ImportNotification(3, true, 12)
        assertEquals("Importação não concluída", result.title)
        assertTrue(result.message.startsWith("12 movimentações importadas."))
        assertTrue(result.message.contains("preservados"))
    }

    @Test fun unsupportedFilesAreWarningsRatherThanFailures() {
        val result = ImportNotification(4, false, 1, skippedDocuments = 2)
        assertEquals("Importação concluída com avisos", result.title)
        assertTrue(result.message.startsWith("1 movimentação importada."))
        assertTrue(result.message.contains("2 mensagens/anexos"))
    }

    @Test fun statementsAwaitingConfirmationAreNotCountedAsImported() {
        val result = ImportNotification(5, false, 0, pendingStatements = 1)
        assertEquals("Importação concluída com avisos", result.title)
        assertTrue(result.message.startsWith("Nenhuma nova movimentação importada."))
        assertTrue(result.message.contains("1 fatura aguarda"))
    }
}
