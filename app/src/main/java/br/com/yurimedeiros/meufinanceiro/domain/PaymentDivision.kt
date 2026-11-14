package br.com.yurimedeiros.meufinanceiro.domain

import java.math.BigDecimal

internal enum class DivisionMode { EQUAL, PERCENT, AMOUNT }

/** Fixed entries are never overwritten; only untouched people receive the remainder. */
internal data class PaymentDivision(
    val total: Long, val people: List<Long> = listOf(0L), val mode: DivisionMode = DivisionMode.EQUAL,
    val fixed: Map<Long, Long> = emptyMap(), val initial: List<Long>? = null,
) {
    init { require(total > 0 && people.isNotEmpty() && people.distinct().size == people.size && people.all { it >= 0 }); require(fixed.keys.all { it in people } && fixed.values.all { it >= 0 }); require(initial == null || (initial.size == people.size && initial.all { it >= 0 } && initial.fold(0L, Math::addExact) == budget)) }
    val budget: Long get() = if (mode == DivisionMode.PERCENT) 10000L else total
    fun units(): List<Long> {
        if (mode == DivisionMode.EQUAL) return PurchaseSplit.equally(total, people.size)
        if (fixed.isEmpty()) return initial ?: PurchaseSplit.equally(budget, people.size)
        val assigned = fixed.values.fold(0L, Math::addExact)
        val remaining = (budget - assigned).coerceAtLeast(0)
        val free = people.filter { it !in fixed }
        val suggested = if (free.isEmpty()) emptyMap() else free.zip(PurchaseSplit.equally(remaining, free.size)).toMap()
        return people.map { fixed[it] ?: suggested.getValue(it) }
    }
    fun amounts(): List<Long> {
        val units = units()
        require(units.fold(0L, Math::addExact) == budget) { if (mode == DivisionMode.PERCENT) "Os percentuais precisam somar 100%." else "Os valores precisam fechar o total da compra." }
        return if (mode == DivisionMode.PERCENT) PurchaseSplit.byPercentages(total, units.map { BigDecimal.valueOf(it, 2) }) else units
    }
    fun percentages(): List<Long> = if (mode == DivisionMode.PERCENT) units() else PurchaseSplit.proportionally(10000, amounts())
    fun edit(person: Long, units: Long) = copy(fixed = fixed + (person to units), initial = null).also { require(mode != DivisionMode.EQUAL) }
    fun mode(next: DivisionMode): PaymentDivision {
        val amounts = runCatching { amounts() }.getOrElse { PurchaseSplit.equally(total, people.size) }
        return copy(mode = next, fixed = emptyMap(), initial = when (next) {
            DivisionMode.EQUAL -> null
            DivisionMode.AMOUNT -> amounts
            DivisionMode.PERCENT -> PurchaseSplit.proportionally(10000, amounts)
        })
    }
}
