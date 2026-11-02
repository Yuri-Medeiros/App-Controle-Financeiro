package br.com.yurimedeiros.meufinanceiro.planning

import br.com.yurimedeiros.meufinanceiro.domain.ReserveLocation
import br.com.yurimedeiros.meufinanceiro.importing.*
import br.com.yurimedeiros.meufinanceiro.ledger.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PlanningRulesTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun settings() = PlanningState(400000, today.minusDays(1), 500000, budgets = mapOf("Casa" to 60000))
    private fun row(id: Long, kind: DocumentKind, date: LocalDate, amount: Long, status: String = "CONFIRMED", statement: LocalDate? = null, category: String = "Casa", paired: Long = 0) = ReviewMovement(id, ImportedRow(kind, date, "Descrição", amount, statement = statement), "source", status, category, "", 1, paired)
    @Test fun confirmedBaseBalanceIncludesOnlySubsequentBankMovementsNotFlash() {
        val rows = listOf(row(1, DocumentKind.NUBANK_ACCOUNT, today.minusDays(1), -90000), row(2, DocumentKind.NUBANK_ACCOUNT, today, -10000), row(3, DocumentKind.FLASH, today, 990000))
        val view = PlanningRules.view(settings(), LedgerSnapshot(), rows, today)
        assertEquals(390000L, view.balance)
        // The bank baseline excludes the older debit, but cycle spending still includes it.
        assertEquals(0L, view.extraBudget)
        assertEquals(390000L, view.available)
    }
    @Test fun familyReceivableNeverInflatesAvailabilityAndInvestedReserveNotDeductedTwice() {
        val ledger = FinanceLedger.replay(listOf(LedgerAction.Debt("d", 1, DebtDirection.RECEIVE, "Familiar", 999999, today)))
        val p = settings().copy(reserves = listOf(ReserveChange("r", "Investida", 80000, ReserveLocation.INVESTMENT, today)))
        assertEquals(340000L, PlanningRules.view(p, ledger, emptyList(), today).available)
    }
    @Test fun futureInvoiceBelongsToNextCycleAndBankReserveIsDeductedOnce() {
        val plan = PurchasePlan("p", "Loja", "Casa", listOf(SchedulePart(1, LocalDate.of(2026, 11, 9), 20000, 1)), mapOf(0L to listOf(10000L), 1L to listOf(10000L)))
        val ledger = FinanceLedger.replay(listOf(LedgerAction.Purchase(plan)))
        val p = settings().copy(reserves = listOf(ReserveChange("r", "Conta", 50000, ReserveLocation.BANK, today)))
        assertEquals(290000L, PlanningRules.view(p, ledger, listOf(row(1, DocumentKind.NUBANK_CARD, today, 20000, statement = LocalDate.of(2026, 11, 8))), today).available)
    }
    @Test fun matchedForecastDoesNotRemainAnObligationAndUsesActualAmount() {
        val e = PlannedExpense("e", "Conta", 20000, today, ExpenseFrequency.ONCE, "Casa")
        val p = settings().copy(expenses = listOf(e))
        val ledger = FinanceLedger.replay(listOf(LedgerAction.Planning(p), LedgerAction.ForecastMatch(e.id, today, 1)))
        val view = PlanningRules.view(p, ledger, listOf(row(1, DocumentKind.NUBANK_ACCOUNT, today, -15000)), today)
        assertEquals(0L, view.obligations)
        assertEquals(45000L, view.extraBudget)
        assertEquals(340000L, view.available)
    }
    @Test fun uncoveredForecastAndBudgetAreNotCountedTwice() {
        val p = settings().copy(expenses = listOf(PlannedExpense("e", "Conta", 20000, today, ExpenseFrequency.ONCE, "Casa")))
        val view = PlanningRules.view(p, LedgerSnapshot(), emptyList(), today)
        assertEquals(20000L, view.obligations)
        assertEquals(40000L, view.extraBudget)
        assertEquals(340000L, view.available)
    }
    @Test fun currentCycleReserveIsAllocatedOnlyOnceAndHistoryCannotBeRewritten() {
        val p = settings().copy(fixedReserve = 30000, reserveStart = "2026-10")
        val additions = PlanningRules.pendingReserve(p, today)
        assertEquals(1, additions.size)
        val saved = p.copy(reserves = additions)
        assertTrue(PlanningRules.pendingReserve(saved, today).isEmpty())
        assertTrue(runCatching { PlanningRules.validate(saved.copy(reserves = emptyList()), saved) }.isFailure)
    }
    @Test fun stoppingRecurrenceKeepsUnmatchedPastOccurrences() {
        val e = PlannedExpense("e", "Aluguel", 10000, LocalDate.of(2026, 9, 5), ExpenseFrequency.MONTHLY, "Casa", active = false, endDate = today)
        val occurrences = PlanningRules.occurrences(e, LocalDate.of(2026, 12, 31))
        assertEquals(listOf(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5)), occurrences.map { it.nominal })
    }
    @Test fun monthEndRecurrenceDoesNotDriftAfterFebruary() {
        val e = PlannedExpense("e", "Conta", 100, LocalDate.of(2026, 1, 31), ExpenseFrequency.MONTHLY, "Casa")
        assertEquals(listOf(31, 28, 31), PlanningRules.occurrences(e, LocalDate.of(2026, 3, 31)).map { it.nominal.dayOfMonth })
    }
    @Test fun municipalOptionalDayIsNotAutomaticallyAHolidayAndFutureYearRequiresConfirmation() {
        assertEquals(LocalDate.of(2026, 6, 5), BankCalendar.adjust(LocalDate.of(2026, 6, 5)))
        assertEquals(LocalDate.of(2026, 7, 10), BankCalendar.adjust(LocalDate.of(2026, 7, 9)))
        assertNull(BankCalendar.adjust(LocalDate.of(2027, 1, 8)))
    }
    @Test fun manualConfirmationUnlocksUnknownSalaryCalendarWithoutInventingHolidays() {
        val jan = LocalDate.of(2027, 1, 6); val feb = LocalDate.of(2027, 2, 5)
        val p = settings().copy(salaryDates = mapOf("2027-01" to jan, "2027-02" to feb))
        assertEquals(feb, PlanningRules.cycle(p, jan)?.next)
    }
    @Test fun intradayBaselineCannotSilentlySkipLaterTransactionsOnTheSameDate() {
        val p = settings().copy(balanceDate = today)
        assertNull(PlanningRules.view(p, LedgerSnapshot(), emptyList(), today).available)
    }
    @Test fun unknownYearProposalStillAvoidsKnownWeekendsWithoutPretendingHolidayValidation() {
        assertEquals(LocalDate.of(2027, 5, 10), BankCalendar.propose(LocalDate.of(2027, 5, 8)))
        assertNull(BankCalendar.adjust(LocalDate.of(2027, 5, 10)))
    }
    @Test fun incompleteBankDataAndConflictsSuppressAnUnsafeAvailableNumber() {
        assertNull(PlanningRules.view(PlanningState(), LedgerSnapshot(), emptyList(), today).available)
        val conflict = row(1, DocumentKind.NUBANK_ACCOUNT, today, -1000, status = "CONFLICT")
        assertNull(PlanningRules.view(settings(), LedgerSnapshot(), listOf(conflict), today).available)
    }
    @Test fun projectionNeverAddsTheNextSalaryToCurrentAvailability() {
        val p = settings()
        val current = PlanningRules.view(p, LedgerSnapshot(), emptyList(), today)
        assertEquals(340000L, current.available)
        val future = PlanningRules.project(p, LedgerSnapshot(), emptyList(), today).first()
        assertEquals(LocalDate.of(2026, 11, 5), future.start)
        assertEquals(780000L, future.available)
    }
    @Test fun ambiguousImportedVersusProjectedInstallmentRequiresLinkingBeforeConsolidation() {
        val due = LocalDate.of(2026, 10, 8)
        val plan = PurchasePlan("p", "Loja", "Casa", listOf(SchedulePart(1, due, 20000)), mapOf(0L to listOf(20000L)))
        val ledger = FinanceLedger.replay(listOf(LedgerAction.Purchase(plan)))
        val view = PlanningRules.view(settings(), ledger, listOf(row(1, DocumentKind.NUBANK_CARD, today.minusDays(20), 20000, statement = due)), today)
        assertNull(view.available)
        assertTrue(view.warnings.any { "Vincule" in it })
    }
}
