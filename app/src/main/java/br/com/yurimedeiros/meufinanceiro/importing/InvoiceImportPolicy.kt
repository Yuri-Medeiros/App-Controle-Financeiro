package br.com.yurimedeiros.meufinanceiro.importing

import java.time.LocalDate
import java.time.YearMonth

/** Import boundary and opening statement: its payment belongs to an older invoice. */
internal object InvoiceImportPolicy {
    val firstMonth: YearMonth = YearMonth.of(2026, 12)
    fun accepts(reference: LocalDate) = YearMonth.from(reference) >= firstMonth

    fun excludedPayments(rows: List<ReviewMovement>, references: List<Pair<String, LocalDate>>): Set<Long> {
        val firstByAccount = (references.filter { accepts(it.second) }.map { it.first to YearMonth.from(it.second) } +
            rows.filter { it.row.kind == DocumentKind.NUBANK_CARD && it.row.statement?.let(::accepts) == true }
                .map { it.origin.substringBefore(" · ") to YearMonth.from(it.row.statement) })
            .groupBy({ it.first }, { it.second }).mapValues { it.value.minOrNull() }
        return rows.filter { movement ->
            val row = movement.row
            row.kind == DocumentKind.NUBANK_CARD && PaymentMatching.isPayment(row) &&
                row.statement?.let { YearMonth.from(it) == firstByAccount[movement.origin.substringBefore(" · ")] } == true
        }.map { it.id }.toSet()
    }
}
