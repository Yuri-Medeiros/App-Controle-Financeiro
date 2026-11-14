package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.importing.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AutomaticInstallmentsTest {
    private val day = LocalDate.of(2026, 12, 8)
    private fun source(id: Long, number: Int = 1, count: Int = 3, cents: Long = 10000, account: String = "a", merchant: String = "Loja") =
        ReviewMovement(id, ImportedRow(DocumentKind.NUBANK_CARD, day, "$merchant - Parcela $number/$count", cents, statement = day.plusMonths(number - 1L)), "$account · arquivo", "PENDING", "Casa", "", 0, 0)
    private fun plan(id: String = "p", sourceId: Long = 1) = LedgerAction.Purchase(PurchasePlan(id, "Nome editado", "Casa",
        (1..3).map { SchedulePart(it, day.plusMonths(it - 1L), 10000, if (it == 1) sourceId else 0) },
        linkedMapOf(0L to listOf(3000L, 3000L, 3000L), 7L to listOf(7000L, 7000L, 7000L)), familyStart = day))
    @Test fun importedNumberedChargeImmediatelyProducesOnlyFutureUnassignedForecasts() {
        val rows = listOf(source(1))
        val invoices = InvoiceBook.invoices(LedgerSnapshot(), rows)
        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2)), invoices.map { it.month })
        assertEquals(listOf(10000L, 10000L, 10000L), invoices.map { it.total })
        assertEquals(listOf(true, false, false), invoices.map { it.imported })
        assertEquals(0L, invoices[1].people.single().items.single().movementId)
        assertEquals(InvoiceBook.UNASSIGNED, invoices[1].people.single().id)
        assertEquals("2/3", invoices[1].people.single().items.single().installment)
        assertTrue(invoices[1].people.single().items.single().title.contains("2/3"))
    }
    @Test fun startingAtSecondInstallmentDoesNotInventAnEarlierInvoice() {
        val invoices = InvoiceBook.invoices(LedgerSnapshot(), listOf(source(2, number = 2, count = 4)))
        assertEquals(listOf(YearMonth.of(2027, 1), YearMonth.of(2027, 2), YearMonth.of(2027, 3)), invoices.map { it.month })
    }
    @Test fun repeatedImportedInstallmentsDoNotMultiplyTheRemainingForecast() {
        val invoices = InvoiceBook.invoices(LedgerSnapshot(), listOf(source(1), source(2, number = 2)))
        assertEquals(listOf(10000L, 10000L, 10000L), invoices.map { it.total })
        val duplicatePurchases = InvoiceBook.invoices(LedgerSnapshot(), listOf(source(1), source(2), source(3, number = 2)))
        assertEquals(listOf(20000L, 20000L, 20000L), duplicatePurchases.map { it.total })
    }
    @Test fun confirmedDivisionReplacesUnassignedForecastAndIsCarriedToAllFollowingParts() {
        val snapshot = FinanceLedger.replay(listOf(plan()))
        val rows = listOf(source(1))
        assertTrue(AutomaticInstallments.forecasts(snapshot, rows).isEmpty())
        val invoices = InvoiceBook.invoices(snapshot, rows)
        assertEquals(listOf(3000L, 7000L), invoices.last().people.map { it.cents })
        assertEquals(7000L, invoices.last().people.single { it.id == 7L }.receivable)
        assertEquals(listOf(10000L, 10000L, 10000L), invoices.map { it.total })
    }
    @Test fun uniqueExactFutureImportLinksWithoutChangingTheConfirmedShares() {
        val before = FinanceLedger.replay(listOf(plan()))
        val rows = listOf(source(1), source(2, number = 2))
        val action = AutomaticInstallments.links(before, rows).single()
        val after = FinanceLedger.apply(before, action)
        assertEquals(2L, after.purchases.single().parts[1].movementId)
        assertEquals(before.purchases.single().shares, after.purchases.single().shares)
        assertEquals(before.debts, after.debts)
        assertEquals(listOf(10000L, 10000L, 10000L), InvoiceBook.invoices(after, rows).map { it.total })
        assertTrue(AutomaticInstallments.links(after, rows).isEmpty())
    }
    @Test fun ambiguousDifferentAccountMerchantAndValueCasesAreLeftForReview() {
        val snapshot = FinanceLedger.replay(listOf(plan()))
        val first = source(1)
        listOf(source(2, 2, account = "b"), source(2, 2, merchant = "Outra loja"), source(2, 2, cents = 11000)).forEach {
            assertTrue(AutomaticInstallments.links(snapshot, listOf(first, it)).isEmpty())
        }
        assertTrue(AutomaticInstallments.links(snapshot, listOf(first, source(2, 2), source(3, 2))).isEmpty())
        val twoPlans = FinanceLedger.replay(listOf(plan(), plan("q", 4)))
        assertTrue(AutomaticInstallments.links(twoPlans, listOf(first, source(4), source(2, 2))).isEmpty())
    }
    @Test fun cashPurchasesRefundsConflictsAndIgnoredRowsDoNotCreateForecasts() {
        val base = source(1)
        val rows = listOf(base.copy(row = base.row.copy(description = "Compra à vista")),
            source(2, cents = -10000), source(3).copy(status = "CONFLICT"), source(4).copy(status = "IGNORED"))
        assertTrue(AutomaticInstallments.forecasts(LedgerSnapshot(), rows).isEmpty())
        assertTrue(AutomaticInstallments.links(LedgerSnapshot(), rows).isEmpty())
    }
}
