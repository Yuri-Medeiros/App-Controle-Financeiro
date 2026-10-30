package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinancialPlanningTest {
    private val now = LocalDate.of(2026, 10, 30)
    private val salary = LocalDate.of(2026, 11, 5)
    private val bills = listOf(AvailableObligation("invoice-includes-family", 120000, now), AvailableObligation("other", 80000, now))
    @Test fun approvedAvailableExampleAndNegativeDeficitKeepExactSign() {
        val reserve = listOf(AvailableReserve(50000, ReserveLocation.BANK))
        val budget = listOf(BudgetNeed(60000, 0))
        assertEquals(90000L, FinancialPlanning.available(400000, now, salary, bills, reserve, budget))
        assertEquals(-60000L, FinancialPlanning.available(250000, now, salary, bills, reserve, budget))
    }
    @Test fun futureCommitmentsPaidBillsAndInvestedReservesAreNotDeductedAgain() {
        val obligations = bills + AvailableObligation("next-cycle", 20000, salary) + AvailableObligation("already-paid", 30000, now, true)
        val reserves = listOf(AvailableReserve(90000, ReserveLocation.INVESTMENT))
        assertEquals(200000L, FinancialPlanning.available(400000, now, salary, obligations, reserves, emptyList()))
    }
    @Test fun budgetCoveredByObligationsIsNotCountedTwice() {
        assertEquals(200000L, FinancialPlanning.available(400000, now, salary, bills, emptyList(), listOf(BudgetNeed(100000, 100000))))
        assertEquals(150000L, FinancialPlanning.available(400000, now, salary, bills, emptyList(), listOf(BudgetNeed(150000, 100000))))
    }
    @Test fun duplicateObligationsAreRejectedRatherThanDoubleCounted() {
        assertTrue(runCatching { FinancialPlanning.available(400000, now, salary, bills + bills, emptyList(), emptyList()) }.isFailure)
    }
    @Test fun salaryDayAdvancesThroughWeekendAndHoliday() {
        val saturday = LocalDate.of(2026, 9, 5)
        val independence = LocalDate.of(2026, 9, 7)
        assertEquals(LocalDate.of(2026, 9, 8), FinancialPlanning.nextBusinessDay(saturday, setOf(independence), setOf(2026)))
    }
    @Test fun unknownCalendarYearRequiresConfirmation() {
        assertTrue(runCatching { FinancialPlanning.nextBusinessDay(LocalDate.of(2027, 1, 8), emptySet(), setOf(2026)) }.isFailure)
    }
    @Test fun salaryBoundaryBelongsToTheNextCycle() {
        val cycle = SalaryCycle(now, salary)
        assertTrue(now in cycle)
        assertFalse(salary in cycle)
    }
}
