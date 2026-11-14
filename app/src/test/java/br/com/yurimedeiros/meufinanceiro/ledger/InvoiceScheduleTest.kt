package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.importing.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InvoiceScheduleTest {
    private fun row(reference: String) = ImportedRow(DocumentKind.NUBANK_CARD, LocalDate.of(2026, 10, 30), "Compra", 12345, statement = LocalDate.parse(reference))
    @Test fun eachMonthStartsAtEightWithoutInheritingPreviousAdjustment() {
        val parts = InvoiceSchedule.parts(row("2026-11-08"), 1, 4, 42)
        assertEquals(listOf("2026-11-09", "2026-12-08", "2027-01-08", "2027-02-10"), parts.map { it.due.toString() })
        assertEquals(listOf(42L, 0L, 0L, 0L), parts.map { it.movementId })
        assertTrue(parts.all { it.cents == 12345L })
    }
    @Test fun importedReferenceDeterminesCurrentPartAndExplicitExceptionIsIsolated() {
        val parts = InvoiceSchedule.parts(row("2027-02-08"), 3, 6, 55,
            mapOf("2027-01" to LocalDate.of(2027, 1, 9)))
        assertEquals(LocalDate.of(2026, 12, 8), parts.first().due)
        assertEquals(LocalDate.of(2027, 1, 11), parts[1].due)
        assertEquals(LocalDate.of(2027, 2, 10), parts[2].due)
        assertEquals(55L, parts[2].movementId)
        assertEquals(1, parts.count { it.movementId != 0L })
    }
    @Test fun linkingCorrectsUnpaidForecastButPreservesRecordedReceipts() {
        val oldDue = LocalDate.of(2026, 12, 9)
        val plan = PurchasePlan("p", "Compra", "Casa", listOf(SchedulePart(1, oldDue, 12345)),
            mapOf(7L to listOf(12345L)), familyStart = LocalDate.of(2026, 11, 1))
        val snapshot = FinanceLedger.apply(LedgerSnapshot(), LedgerAction.Purchase(plan))
        val source = ReviewMovement(42, row("2026-12-08"), "conta", "CONFIRMED", "Casa", "", 0, 0)
        val action = InvoiceSchedule.linkAction(snapshot, "p", 1, source, mapOf(7L to 12345L))
        assertTrue(action is LedgerAction.AdjustLink)
        val adjusted = FinanceLedger.apply(snapshot, action)
        assertEquals(LocalDate.of(2026, 12, 8), adjusted.purchases.single().parts.single().due)
        assertEquals(12345L, adjusted.debts.single().originalCents)
        val paid = FinanceLedger.apply(snapshot, LedgerAction.Payment(7, DebtDirection.RECEIVE, 99,
            oldDue.minusDays(1), mapOf("p:1:7" to 1000L)))
        val link = InvoiceSchedule.linkAction(paid, "p", 1, source, mapOf(7L to 12345L))
        assertTrue(link is LedgerAction.Link)
        val linked = FinanceLedger.apply(paid, link)
        assertEquals(oldDue, linked.purchases.single().parts.single().due)
        assertEquals(1000L, linked.debts.single().paidCents)
        assertEquals(LocalDate.of(2026, 12, 8), InvoiceBook.invoices(linked, listOf(source)).single().due)
        val changed = InvoiceSchedule.linkAction(paid, "p", 1, source.copy(row = source.row.copy(cents = 13000)), mapOf(7L to 13000L))
        assertTrue(runCatching { FinanceLedger.apply(paid, changed) }.isFailure)
    }

}
