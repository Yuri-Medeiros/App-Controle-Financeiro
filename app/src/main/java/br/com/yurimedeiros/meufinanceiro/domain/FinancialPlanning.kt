package br.com.yurimedeiros.meufinanceiro.domain

import java.time.DayOfWeek
import java.time.LocalDate

internal enum class ReserveLocation { BANK, INVESTMENT }
internal data class AvailableObligation(val id: String, val cents: Long, val due: LocalDate, val paid: Boolean = false)
internal data class AvailableReserve(val cents: Long, val location: ReserveLocation)
internal data class BudgetNeed(val remainingCents: Long, val coveredByObligationsCents: Long)
internal data class SalaryCycle(val start: LocalDate, val next: LocalDate) {
    init { require(next > start) }
    operator fun contains(date: LocalDate) = date >= start && date < next
}

/** The caller must supply confirmed account balance and known obligations, never a guessed balance. */
internal object FinancialPlanning {
    fun available(
        bankBalanceCents: Long, today: LocalDate, nextSalary: LocalDate,
        obligations: List<AvailableObligation>, reserves: List<AvailableReserve>, budget: List<BudgetNeed>,
    ): Long {
        require(nextSalary > today)
        require(obligations.map { it.id }.distinct().size == obligations.size) { "Obrigação duplicada." }
        require(obligations.all { it.cents >= 0 } && reserves.all { it.cents >= 0 })
        require(budget.all { it.remainingCents >= 0 && it.coveredByObligationsCents >= 0 })
        val payable = obligations.filter { !it.paid && it.due < nextSalary }.map { it.cents }.fold(0L, Math::addExact)
        val reserved = reserves.filter { it.location == ReserveLocation.BANK }.map { it.cents }.fold(0L, Math::addExact)
        val extraBudget = budget.map { (it.remainingCents - it.coveredByObligationsCents).coerceAtLeast(0) }.fold(0L, Math::addExact)
        return Math.subtractExact(Math.subtractExact(Math.subtractExact(bankBalanceCents, payable), reserved), extraBudget)
    }
    fun nextBusinessDay(date: LocalDate, holidays: Set<LocalDate>, validatedYears: Set<Int>): LocalDate {
        var result = date
        repeat(370) {
            require(result.year in validatedYears) { "Calendário de ${result.year} ainda não validado; confirme a data." }
            if (result.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && result !in holidays) return result
            result = result.plusDays(1)
        }
        error("Calendário inválido.")
    }
}
