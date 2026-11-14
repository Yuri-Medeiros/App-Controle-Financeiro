package br.com.yurimedeiros.meufinanceiro.ui

import br.com.yurimedeiros.meufinanceiro.importing.DocumentKind
import br.com.yurimedeiros.meufinanceiro.importing.ImportedRow
import br.com.yurimedeiros.meufinanceiro.importing.ReviewMovement
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BankMovementChoiceTest {
    private fun movement(description: String, cents: Long = 100, friendlyName: String = "") = ReviewMovement(
        42, ImportedRow(DocumentKind.NUBANK_ACCOUNT, LocalDate.of(2026, 11, 13), description, cents), "test", "CONFIRMED", "", friendlyName, 0, 0)

    @Test fun transferShowsOnlyTheCounterpartyWithoutBankDocumentOrAccountData() {
        for (prefix in listOf("Transferência recebida pelo Pix", "Transferência Recebida", "Transferência enviada pelo Pix")) {
            val row = movement("$prefix - Pessoa Exemplo - •••.123.456-•• - BANCO EXEMPLO Agência: 1 Conta: 123-4")
            assertEquals("Pessoa Exemplo", bankMovementParty(row))
            assertEquals(42L, row.id)
            assertTrue(row.row.description.contains("Conta: 123-4"))
        }
    }

    @Test fun friendlyNameTakesPriorityAndUnrecognizedRowsNeverDumpTheRawDescription() {
        assertEquals("Minha corretora", bankMovementParty(movement("descrição extensa", friendlyName = " Minha corretora ")))
        assertEquals("Origem não identificada", bankMovementParty(movement("Crédito com dados não reconhecidos Agência: 1 Conta: 123")))
        assertEquals("Destino não identificado", bankMovementParty(movement("Débito com dados não reconhecidos", cents = -100)))
        assertEquals("Loja Exemplo", bankMovementParty(movement("Compra no débito - Loja Exemplo", cents = -100)))
    }
}
