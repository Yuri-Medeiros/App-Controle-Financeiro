package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.importing.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InvoiceBookTest {
    private val due = LocalDate.of(2026, 11, 9)
    private val month = YearMonth.from(due)
    private fun row(id: Long, cents: Long, kind: DocumentKind = DocumentKind.NUBANK_CARD, reference: LocalDate? = due, status: String = "CONFIRMED", pair: Long = 0, date: LocalDate = due, description: String = "Compra") =
        ReviewMovement(id, ImportedRow(kind, date, description, cents, statement = reference), "conta", status, "Casa", "", 0, pair)
    private fun plan() = LedgerAction.Purchase(PurchasePlan("p", "Compartilhada", "Casa",
        listOf(SchedulePart(1, due, 20000, 1), SchedulePart(2, due.plusMonths(1), 20000)),
        linkedMapOf(0L to listOf(10000L, 10000L), 7L to listOf(10000L, 10000L)), familyStart = due))
    private fun rows() = listOf(row(1, 20000),
        row(2, -20000, status = "PAYMENT", pair = 3, description = "Pagamento recebido"),
        row(3, -20000, DocumentKind.NUBANK_ACCOUNT, null, "PAYMENT", 2, description = "Pagamento de fatura"))
    private fun snapshot(vararg extra: LedgerAction) = FinanceLedger.replay(listOf(plan()) + extra)
    private fun invoice(ledger: LedgerSnapshot = snapshot(), movements: List<ReviewMovement> = rows()) = InvoiceBook.invoices(ledger, movements).single { it.month == month }

    @Test fun importedAndPlannedInstallmentAreNeverCountedTwiceAfterLink() {
        val invoices = InvoiceBook.invoices(snapshot(), rows())
        assertEquals(listOf(20000L, 20000L), invoices.map { it.total })
        assertEquals(2, invoices.first().people.size)
        assertEquals(0L, invoices.first().remaining)
        assertFalse(invoices.last().imported)
    }
    @Test fun noOwnerIsInventedForCategorizedPurchaseAndUnresolvedOverlapWarns() {
        val inv = invoice(movements = rows() + row(4, 999, reference = due.plusMonths(1)))
        assertEquals(20000L, inv.total)
        val next = InvoiceBook.invoices(snapshot(), rows() + row(4, 999, reference = due.plusMonths(1))).last()
        assertEquals(999L, next.people.single { it.id == InvoiceBook.UNASSIGNED }.cents)
        assertTrue(next.warnings.any { "duas vezes" in it })
    }
    @Test fun identicalUnassignedItemsKeepTheirOwnMovementIdentity() {
        val movements = listOf(row(40, 12345), row(41, 12345))
        val inv = invoice(LedgerSnapshot(), movements)
        val items = inv.people.single { it.id == InvoiceBook.UNASSIGNED }.items
        assertEquals(2, items.size)
        assertEquals(setOf(40L, 41L), items.map { it.movementId }.toSet())
        assertEquals(24690L, inv.total)
        // Assigning one original source must leave the other row pending.
        val action = LedgerAction.Purchase(PurchasePlan("new", "Compra", "Casa",
            listOf(SchedulePart(1, due, 12345, items.first().movementId)), mapOf(7L to listOf(12345L)), familyStart = due))
        val after = invoice(FinanceLedger.apply(LedgerSnapshot(), action), movements)
        assertEquals(12345L, after.people.single { it.id == 7L }.receivable)
        assertEquals(41L, after.people.single { it.id == InvoiceBook.UNASSIGNED }.items.single().movementId)
        assertEquals(inv.total, after.total)
    }
    @Test fun linkedItemsKeepTheSourceAndForecastsHaveNoInventedSource() {
        val all = InvoiceBook.invoices(snapshot(), rows())
        assertEquals(1L, all.first().people.single { it.id == 7L }.items.single().movementId)
        assertEquals(1L, all.first().people.single { it.id == 0L }.items.single().movementId)
        assertTrue(all.last().people.flatMap { it.items }.all { it.movementId == 0L })
    }

    @Test fun bankSettlementAndAttributionNeverPayFamilyReceivables() {
        val action = LedgerAction.InvoiceAllocation(due, 3, 2, mapOf(0L to 10000, 7L to 10000))
        InvoiceBook.validateAllocation(action, snapshot(), rows())
        val ledger = snapshot(action)
        val inv = invoice(ledger)
        assertEquals(10000L, inv.people.single { it.id == 7L }.receivable)
        assertEquals(0L, inv.people.single { it.id == 7L }.received)
        assertEquals(10000L, inv.people.single { it.id == 7L }.bankPaid)
        assertEquals(0L, inv.unallocated)
        assertEquals(setOf(1L, 2L, 3L), ledger.usedMovementIds)
    }
    @Test fun suggestionsUseOnlyReceiptsForThisInvoiceAndDoNotInventOpeningReceipts() {
        val received = LedgerAction.Payment(7, DebtDirection.RECEIVE, 8, due, mapOf("p:1:7" to 4000))
        val inv = invoice(snapshot(received))
        assertEquals(mapOf(7L to 4000L, 0L to 6000L), InvoiceBook.suggestBankAllocation(inv, 10000))
        val historical = plan().plan.copy(openingBalances = mapOf("1:7" to 5000L))
        val opening = invoice(FinanceLedger.replay(listOf(LedgerAction.Purchase(historical))))
        assertEquals(mapOf(0L to 10000L), InvoiceBook.suggestBankAllocation(opening, 20000))
        val futureOnly = snapshot(LedgerAction.Payment(7, DebtDirection.RECEIVE, 8, due, mapOf("p:2:7" to 9000)))
        assertEquals(mapOf(0L to 10000L), InvoiceBook.suggestBankAllocation(invoice(futureOnly), 20000))
    }
    @Test fun previousDebtsRequireBankSettlementAndRemainSeparatedFromSelectedInvoice() {
        val all = InvoiceBook.invoices(snapshot(), rows())
        val debt = InvoiceBook.previousDebts(all.last(), all, snapshot(), rows()).single()
        assertEquals("p:1:7", debt.id)
        val unpaid = InvoiceBook.invoices(snapshot(), listOf(row(1, 20000)))
        assertTrue(InvoiceBook.previousDebts(unpaid.last(), unpaid, snapshot(), listOf(row(1, 20000))).isEmpty())
    }
    @Test fun nextUnpaidInvoiceIsDefaultEvenIfOlderThanCurrentMonth() {
        val all = InvoiceBook.invoices(snapshot(), rows())
        assertEquals(month.plusMonths(1), InvoiceBook.defaultMonth(all, due))
        assertEquals(month, InvoiceBook.defaultMonth(InvoiceBook.invoices(snapshot(), listOf(row(1, 20000))), due.plusMonths(2)))
        assertNull(InvoiceBook.defaultMonth(emptyList(), due))
    }
    @Test fun pendingPaymentDoesNotReduceBankObligation() {
        val inv = invoice(movements = listOf(row(1, 20000), row(2, -20000, status = "PAYMENT_PENDING", description = "Pagamento recebido")))
        assertEquals(0L, inv.paid)
        assertEquals(20000L, inv.remaining)
        assertTrue(inv.warnings.any { "conciliação" in it })
    }
    @Test fun latestImportedReferenceWinsOverOldPlannedMonthWithoutDuplicatingPart() {
        val shifted = rows().map { if (it.id == 1L) it.copy(row = it.row.copy(statement = due.plusMonths(1))) else it }
        val all = InvoiceBook.invoices(snapshot(), shifted)
        assertEquals(0L, all.first().total)
        assertEquals(40000L, all.last().total)
        assertEquals(month.plusMonths(1), InvoiceBook.debtMonth(snapshot().debts.first(), snapshot(), shifted))
    }
    @Test fun refundsReduceBankInvoiceAndOwnersWithoutErasingPastReceipts() {
        val state = snapshot(LedgerAction.Payment(7, DebtDirection.RECEIVE, 8, due, mapOf("p:1:7" to 10000)), LedgerAction.Refund("p", 9, due, mapOf("p:1:7" to 4000)))
        val inv = invoice(state, rows() + row(9, -4000, status = "REFUND"))
        val person = inv.people.single { it.id == 7L }
        assertEquals(16000L, inv.total)
        assertEquals(6000L, person.cents)
        assertEquals(9L, person.items.single { it.cents < 0 }.movementId)
        assertEquals(10000L, person.received)
        assertEquals(4000L, state.debts.single { it.direction == DebtDirection.PAY }.remaining)
        // A bank payment made before the refund can still be attributed later in full.
        val allocation = LedgerAction.InvoiceAllocation(due, 3, 2, mapOf(0L to 10000L, 7L to 10000L))
        InvoiceBook.validateAllocation(allocation, state, rows() + row(9, -4000, status = "REFUND"))
        val after = invoice(FinanceLedger.apply(state, allocation), rows() + row(9, -4000, status = "REFUND"))
        assertEquals(10000L, after.people.single { it.id == 7L }.bankPaid)
        assertEquals(0L, after.unallocated)
    }
    @Test fun allocationRejectsOverflowWrongMonthAndUnpairedOrExcessiveAmounts() {
        val ledger = snapshot()
        val good = LedgerAction.InvoiceAllocation(due, 3, 2, mapOf(0L to 10000L, 7L to 10000L))
        listOf(good.copy(allocations = mapOf(0L to 20000)), good.copy(reference = due.plusMonths(1)), good.copy(allocations = mapOf(0L to 9999L, 7L to 10000L))).forEach {
            assertTrue(runCatching { InvoiceBook.validateAllocation(it, ledger, rows()) }.isFailure)
        }
        assertTrue(runCatching { InvoiceBook.validateAllocation(good, ledger, rows().map { it.copy(pairedMovementId = 0) }) }.isFailure)
        assertTrue(runCatching { FinanceLedger.apply(ledger, good.copy(allocations = mapOf(0L to Long.MAX_VALUE, 7L to Long.MAX_VALUE))) }.isFailure)
        assertTrue(runCatching { FinanceLedger.apply(FinanceLedger.apply(ledger, good), good) }.isFailure)
    }
    @Test fun oldestReceiptSuggestionPreservesCentAndPartialRemainder() {
        val debts = listOf(DebtBalance("c", 7, DebtDirection.RECEIVE, "c", due.plusMonths(1), 10000), DebtBalance("b", 7, DebtDirection.RECEIVE, "b", due, 15000), DebtBalance("a", 7, DebtDirection.RECEIVE, "a", due.minusMonths(1), 30000))
        assertEquals(linkedMapOf("a" to 30000L, "b" to 15000L, "c" to 5000L), InvoiceBook.suggestReceipt(debts, 50000))
        assertEquals(mapOf("a" to 1L), InvoiceBook.suggestReceipt(debts, 1))
    }
    @Test fun calendarsOutsideValidatedYearRequireConfirmationAndCanBeOverridden() {
        val future = plan().plan.copy(parts = listOf(SchedulePart(1, LocalDate.of(2027, 1, 8), 20000, 1)), shares = mapOf(0L to listOf(20000L)), familyOpening = setOf(1))
        val ledger = FinanceLedger.replay(listOf(LedgerAction.Purchase(future)))
        // Known explicitly registered installment dates are retained.
        assertEquals(future.parts.single().due, InvoiceBook.invoices(ledger, emptyList()).single().due)
        val rows = listOf(row(1, 20000, reference = LocalDate.of(2027, 2, 8)))
        val unknown = InvoiceBook.invoices(LedgerSnapshot(), rows).single()
        assertNull(unknown.due)
        val settings = LedgerAction.Planning(LedgerSnapshot().planning.copy(invoiceDates = mapOf("2027-02" to LocalDate.of(2027, 2, 10))))
        assertEquals(LocalDate.of(2027, 2, 10), InvoiceBook.invoices(FinanceLedger.replay(listOf(settings)), rows).single().due)
    }
    @Test fun mixedOldAndNewJournalReplayPreservesAmountsNamesAndAllocations() {
        val original = listOf(plan(), LedgerAction.Payment(7, DebtDirection.RECEIVE, 8, due, mapOf("p:1:7" to 2500)), LedgerAction.InvoicePaid(due, 3, 2))
        val allocation = LedgerAction.InvoiceAllocation(due, 3, 2, linkedMapOf(0L to 10000L, 7L to 10000L))
        val roundtrip = (original + allocation).map { LedgerCodec.decode(LedgerCodec.encode(it)) }
        val restored = FinanceLedger.replay(roundtrip)
        assertEquals(snapshot().purchases, restored.purchases)
        assertEquals(7500L, restored.debts.first().remaining)
        assertEquals(original + allocation, restored.actions)
        assertEquals(20000L, invoice(restored).paid)
        assertEquals(10000L, invoice(restored).people.single { it.id == 7L }.bankPaid)
    }
}
