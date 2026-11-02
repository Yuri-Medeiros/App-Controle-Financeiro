package br.com.yurimedeiros.meufinanceiro.ui

import br.com.yurimedeiros.meufinanceiro.importing.DocumentKind
import br.com.yurimedeiros.meufinanceiro.importing.ImportedRow
import br.com.yurimedeiros.meufinanceiro.importing.ReviewMovement
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class MovementFiltersTest {
    private val period = YearMonth.of(2026, 10)
    private fun movement(
        id: Long, date: String, description: String = "Compra no débito - MERCADO ORIGINAL",
        friendly: String = "", status: String = "PENDING",
    ) = ReviewMovement(
        id, ImportedRow(DocumentKind.NUBANK_ACCOUNT, LocalDate.parse(date), description, -100),
        "account-a · sample.csv", status, "", friendly, 0, 0,
    )

    @Test fun restrictsBothMonthAndYearIncludingTheirBoundaries() {
        val rows = listOf(movement(1, "2026-09-30"), movement(2, "2026-10-01"),
            movement(3, "2026-10-31"), movement(4, "2026-11-01"), movement(5, "2025-10-31"))
        assertEquals(listOf(3L, 2L), MovementFilters.filterAndSort(rows, null, period, "").map { it.id })
    }

    @Test fun sortsAllRecordsDescendingWithoutPrioritizingPending() {
        val rows = listOf(movement(1, "2026-10-01"), movement(2, "2026-10-28", status = "CONFIRMED"))
        assertEquals(listOf(2L, 1L), MovementFilters.filterAndSort(rows, null, period, "").map { it.id })
    }

    @Test fun searchesOriginalAndRegisteredFriendlyDescriptions() {
        val rows = listOf(movement(1, "2026-10-28", friendly = "Mercado do bairro"))
        assertEquals(1, MovementFilters.filterAndSort(rows, null, period, "ORIGINAL").size)
        assertEquals(1, MovementFilters.filterAndSort(rows, null, period, "  DO BAIRRO ").size)
    }

    @Test fun findsInheritedDescriptionFromAnotherMonthWithoutMixingAccounts() {
        val seed = movement(1, "2026-09-10", friendly = "Mercado do bairro", status = "CONFIRMED")
        val october = movement(2, "2026-10-28")
        val anotherAccount = october.copy(id = 3, origin = "account-b · sample.csv")
        val result = MovementFilters.filterAndSort(listOf(seed, october, anotherAccount), null, period, "do bairro")
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test fun accountFilterAndDateSearchRemainCombinedWithPeriod() {
        val account = movement(1, "2026-10-28")
        val flash = account.copy(id = 2, row = account.row.copy(kind = DocumentKind.FLASH))
        assertEquals(listOf(2L), MovementFilters.filterAndSort(listOf(account, flash), DocumentKind.FLASH, period, "28/10/2026").map { it.id })
    }

    @Test fun sortsSameDayByTimeDescending() {
        val first = movement(1, "2026-10-28")
        val morning = first.copy(row = first.row.copy(time = LocalTime.of(8, 0)))
        val evening = first.copy(id = 2, row = first.row.copy(time = LocalTime.of(18, 0)))
        assertEquals(listOf(2L, 1L), MovementFilters.filterAndSort(listOf(morning, evening), null, period, "").map { it.id })
    }
}
