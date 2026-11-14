package br.com.yurimedeiros.meufinanceiro.importing

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InvoiceImportPolicyTest {
    private fun movement(id: Long, reference: String, title: String = "Pagamento recebido", cents: Long = -10000, account: String = "a", kind: DocumentKind = DocumentKind.NUBANK_CARD) =
        ReviewMovement(id, ImportedRow(kind, LocalDate.of(2026, 11, 20), title, cents, statement = LocalDate.parse(reference)), "$account · arquivo.csv", "PENDING", "", "", 0, 0)
    @Test fun boundaryUsesReferenceRatherThanPurchaseDate() {
        assertFalse(InvoiceImportPolicy.accepts(LocalDate.of(2026, 11, 30)))
        assertTrue(InvoiceImportPolicy.accepts(LocalDate.of(2026, 12, 1)))
        assertTrue(InvoiceImportPolicy.accepts(LocalDate.of(2027, 1, 8)))
    }
    @Test fun openingStatementExcludesOnlyPaymentsAndKeepsCreditsAndAccountTransfers() {
        val rows = listOf(movement(1, "2026-12-08"), movement(2, "2026-12-08", "Estorno", -1000),
            movement(3, "2026-12-08", "Reembolso", -2000), movement(4, "2026-12-08", "Compra", 5000),
            movement(5, "2026-12-08", "Pagamento de fatura", kind = DocumentKind.NUBANK_ACCOUNT), movement(6, "2027-01-08"))
        assertEquals(setOf(1L), InvoiceImportPolicy.excludedPayments(rows, emptyList()))
    }
    @Test fun earliestAcceptedMonthIsPerAccountAndIndependentOfArrivalOrder() {
        val january = movement(1, "2027-01-08")
        assertEquals(setOf(1L), InvoiceImportPolicy.excludedPayments(listOf(january), emptyList()))
        val december = movement(2, "2026-12-08")
        val anotherAccount = movement(3, "2027-02-08", account = "b")
        val rows = listOf(january, anotherAccount, december)
        assertEquals(setOf(2L, 3L), InvoiceImportPolicy.excludedPayments(rows, emptyList()))
        assertEquals(setOf(2L, 3L), InvoiceImportPolicy.excludedPayments(rows.reversed(), emptyList()))
    }
    @Test fun emptyOpeningDocumentAndRejectedOlderDocumentsDoNotBreakBoundary() {
        val rows = listOf(movement(1, "2027-01-08"))
        assertEquals(emptySet<Long>(), InvoiceImportPolicy.excludedPayments(rows, listOf("a" to LocalDate.of(2026, 12, 8))))
        assertEquals(setOf(1L), InvoiceImportPolicy.excludedPayments(rows, listOf("a" to LocalDate.of(2026, 11, 8))))
    }
}
