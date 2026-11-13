package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.importing.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeopleInsightsTest {
    private val due = LocalDate.of(2026, 11, 9)
    private val month = YearMonth.from(due)
    private val today = due.plusDays(4)
    private fun plan() = LedgerAction.Purchase(PurchasePlan("p", "Compra dividida", "Casa",
        listOf(SchedulePart(1, due, 20000, 1), SchedulePart(2, due.plusMonths(1), 20000)),
        linkedMapOf(0L to listOf(10000L, 10000L), 7L to listOf(10000L, 10000L)), familyStart = due))
    private fun row(id: Long, cents: Long, kind: DocumentKind = DocumentKind.NUBANK_CARD, pair: Long = 0, description: String = "Compra", status: String = "CONFIRMED", reference: LocalDate? = due) =
        ReviewMovement(id, ImportedRow(kind, due, description, cents, statement = reference), "teste", status, "Casa", "", 0, pair)
    private fun rows() = listOf(row(1, 20000), row(2, -20000, pair = 3, description = "Pagamento recebido", status = "PAYMENT"),
        row(3, -20000, DocumentKind.NUBANK_ACCOUNT, 2, "Pagamento de fatura", "PAYMENT", null))
    private fun ledger(vararg more: LedgerAction) = FinanceLedger.replay(listOf(plan()) + more)
    private fun overview(s: LedgerSnapshot = ledger(), r: List<ReviewMovement> = rows(), reference: YearMonth = month) =
        PeopleInsights.overview(s, r, InvoiceBook.invoices(s, r), listOf(7, 8, 0), reference, today)
    private fun manual(id: String, cents: Long, direction: DebtDirection, date: LocalDate = due) = LedgerAction.Debt(id, 7, direction, "Avulsa $id", cents, date)

    @Test fun totalsSeparateFutureAndPayablesAndNeverIncludeOwnShares() {
        val s = ledger(manual("receive", 5000, DebtDirection.RECEIVE), manual("pay", 3000, DebtDirection.PAY),
            manual("future", 1200, DebtDirection.RECEIVE, due.plusMonths(2)))
        val view = overview(s)
        assertEquals(15000L, view.receivable)
        assertEquals(15000L, view.overdue)
        assertEquals(3000L, view.payable)
        assertEquals(10000L, view.futureInstallments)
        val person = view.people.single { it.id == 7L }
        assertEquals(11200L, person.futureReceivable)
        assertEquals(due.plusMonths(1), person.nextDue)
        assertFalse(person.clear)
        assertTrue(view.people.single { it.id == 8L }.clear)
        assertTrue(view.people.none { it.id == 0L })
    }
    @Test fun selectingAnotherInvoiceChangesBucketsWithoutMutatingHistory() {
        val s = ledger(LedgerAction.Payment(7, DebtDirection.RECEIVE, 8, due, mapOf("p:1:7" to 4000)))
        val originalActions = s.actions.toList()
        val nov = overview(s)
        val dec = overview(s, reference = month.plusMonths(1))
        assertEquals(6000L, nov.receivable)
        assertEquals(16000L, dec.receivable)
        assertEquals(10000L, nov.futureInstallments)
        assertEquals(0L, dec.futureInstallments)
        assertEquals(6000L, dec.overdue)
        assertEquals(originalActions, s.actions)
    }
    @Test fun bankAttributionDoesNotChangeFamilyBalancesOrReceipts() {
        val a = LedgerAction.InvoiceAllocation(due, 3, 2, mapOf(0L to 10000L, 7L to 10000L))
        val view = overview(ledger(a))
        assertEquals(10000L, view.receivable)
        val share = view.people.first().invoices.single { it.invoice.month == month }.share
        assertEquals(0L, share.received)
        assertEquals(10000L, share.bankPaid)
        assertEquals(10000L, share.receivable)
    }
    @Test fun partialOpeningNeverBecomesAnInventedHistoricalReceipt() {
        val opening = plan().plan.copy(familyOpening = setOf(1), openingBalances = mapOf("1:7" to 4500L))
        val s = FinanceLedger.replay(listOf(LedgerAction.Purchase(opening)))
        val person = overview(s).people.first()
        assertEquals(4500L, person.receivable)
        assertEquals(0L, person.futureInstallments)
        assertEquals(0L, person.invoices.single { it.invoice.month == month }.share.received)
        assertTrue(PeopleInsights.history(7, s, rows()).isEmpty())
    }
    @Test fun refundAfterReceiptRemainsASeparatePayable() {
        val s = ledger(LedgerAction.Payment(7, DebtDirection.RECEIVE, 8, due, mapOf("p:1:7" to 10000)),
            LedgerAction.Refund("p", 9, due, mapOf("p:1:7" to 4000)))
        val view = overview(s, rows() + row(9, -4000, status = "REFUND"))
        assertEquals(0L, view.receivable)
        assertEquals(4000L, view.payable)
        assertEquals(10000L, view.futureInstallments)
        assertFalse(view.people.first().clear)
    }
    @Test fun importedReferenceDeterminesTheBucketOfALinkedInstallment() {
        val shifted = rows().map { if (it.id == 1L) it.copy(row = it.row.copy(statement = due.plusMonths(1))) else it }
        val view = overview(r = shifted)
        assertEquals(month.plusMonths(1), view.debtMonths["p:1:7"])
        assertEquals(0L, view.receivable)
        assertEquals(20000L, view.futureInstallments)
    }
    @Test fun manualDatesAndInvoiceDueOverridesAreHonoredWithoutPostponingCardDebts() {
        val s = ledger(manual("r", 2000, DebtDirection.RECEIVE, due), LedgerAction.Postpone("r", due.plusDays(15), "Combinado"),
            LedgerAction.Planning(LedgerSnapshot().planning.copy(invoiceDates = mapOf(month.toString() to due.plusDays(7)))))
        val view = overview(s)
        assertEquals(due.plusDays(7), view.outsideCutoff)
        assertEquals(10000L, view.receivable)
        assertEquals(0L, view.overdue)
        assertEquals(12000L, view.people.first().futureReceivable)
        assertEquals(10000L, view.futureInstallments)
        assertEquals(due.plusDays(7), view.people.first().nextDue)
        assertEquals(due, s.purchases.single().parts.first().due)
    }
    @Test fun summaryDoesNotExposePurchasesOrBankPaymentsOrOffsetDirections() {
        val s = ledger(manual("pay", 3000, DebtDirection.PAY), LedgerAction.InvoiceAllocation(due, 3, 2, mapOf(0L to 10000L, 7L to 10000L)))
        val view = overview(s)
        val text = PeopleInsights.statement("Pessoa", view, view.people.first())
        assertTrue(text.contains("11/2026"))
        assertTrue(text.contains("A receber:"))
        assertTrue(text.contains("Eu devo:"))
        assertTrue(text.contains("100,00"))
        assertTrue(text.contains("30,00"))
        assertFalse(text.contains("70,00"))
        assertFalse(text.contains("Compra dividida"))
        assertFalse(text.contains("banco"))
        assertFalse(text.contains("12/2026"))
    }
    @Test fun detailedStatementUsesOnlyThisPersonAndOneInvoice() {
        val s = ledger(LedgerAction.Payment(7, DebtDirection.RECEIVE, 8, due, mapOf("p:1:7" to 4000)))
        val view = overview(s)
        val text = PeopleInsights.statement("Pessoa", view, view.people.first(), month)
        assertTrue(text.contains("Compra dividida · Parcela 1/2"))
        assertTrue(text.contains("100,00"))
        assertTrue(text.contains("40,00"))
        assertTrue(text.contains("60,00"))
        assertFalse(text.contains("200,00"))
        assertFalse(text.contains("Parcela 2/2"))
    }
    @Test fun historyFiltersBeforePaginationAndDoesNotInferUnallocatedBankPayments() {
        val s = ledger(LedgerAction.Payment(7, DebtDirection.RECEIVE, 8, due, mapOf("p:1:7" to 4000)),
            LedgerAction.InvoiceAllocation(due, 3, 2, mapOf(0L to 10000L, 7L to 10000L)),
            LedgerAction.Debt("other", 8, DebtDirection.PAY, "Outra pessoa", 999, due))
        val history = PeopleInsights.history(7, s, rows())
        assertEquals(listOf("Pago ao banco", "Recebido"), history.map { it.title })
        assertEquals(listOf(10000L, 4000L), history.map { it.cents })
        assertFalse(history.any { it.detail.contains("Outra pessoa") })
        assertTrue(PeopleInsights.history(7, ledger(), rows()).isEmpty())
    }
}
