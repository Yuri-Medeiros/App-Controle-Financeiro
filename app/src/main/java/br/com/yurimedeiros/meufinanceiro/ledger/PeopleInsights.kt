package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.formatting.AppDates
import br.com.yurimedeiros.meufinanceiro.importing.ReviewMovement
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

internal data class PersonInvoice(val invoice: Invoice, val share: InvoicePerson)
internal data class PersonSummary(
    val id: Long, val receivable: Long, val payable: Long, val overdue: Long,
    val futureReceivable: Long, val futurePayable: Long, val futureInstallments: Long,
    val nextDue: LocalDate?, val oldestOverdue: LocalDate?,
    val invoices: List<PersonInvoice>, val outside: List<DebtBalance>,
) {
    val clear get() = receivable == 0L && payable == 0L && futureReceivable == 0L && futurePayable == 0L
}
internal data class PeopleOverview(
    val reference: YearMonth, val outsideCutoff: LocalDate, val people: List<PersonSummary>,
    val debtMonths: Map<String, YearMonth>,
) {
    val receivable get() = people.sumOf { it.receivable }
    val payable get() = people.sumOf { it.payable }
    val overdue get() = people.sumOf { it.overdue }
    val futureInstallments get() = people.sumOf { it.futureInstallments }
}
internal data class PersonHistoryEntry(val date: LocalDate?, val title: String, val cents: Long?, val detail: String)

/** Current reconciled family balances, segmented by invoice; never reconstructs imaginary past receipts. */
internal object PeopleInsights {
    fun overview(ledger: LedgerSnapshot, rows: List<ReviewMovement>, invoices: List<Invoice>, ids: List<Long>, reference: YearMonth, today: LocalDate): PeopleOverview {
        val sources = rows.associateBy { it.id }
        val parts = ledger.purchases.flatMap { p -> p.parts.map { part ->
            (p.id to part.number) to (sources[part.movementId]?.row?.statement?.let(YearMonth::from) ?: YearMonth.from(part.due))
        } }.toMap()
        val debtMonths = ledger.debts.mapNotNull { debt -> parts[debt.purchaseId to debt.number]?.let { debt.id to it } }.toMap()
        val byMonth = invoices.associateBy { it.month }
        val cutoff = byMonth[reference]?.due ?: reference.atEndOfMonth()
        fun due(d: DebtBalance) = debtMonths[d.id]?.let { byMonth[it]?.due } ?: d.due
        fun current(d: DebtBalance) = debtMonths[d.id]?.let { it <= reference } ?: (d.due <= cutoff)
        val debtsByPerson = ledger.debts.groupBy { it.personId }
        return PeopleOverview(reference, cutoff, ids.distinct().filter { it > 0 }.map { id ->
            val debts = debtsByPerson[id].orEmpty()
            val now = debts.filter(::current)
            val future = debts.filterNot(::current)
            val late = now.filter { it.direction == DebtDirection.RECEIVE && it.remaining > 0 && due(it) < today }
            PersonSummary(id,
                now.filter { it.direction == DebtDirection.RECEIVE }.sumOf { it.remaining },
                now.filter { it.direction == DebtDirection.PAY }.sumOf { it.remaining },
                late.sumOf { it.remaining },
                future.filter { it.direction == DebtDirection.RECEIVE }.sumOf { it.remaining },
                future.filter { it.direction == DebtDirection.PAY }.sumOf { it.remaining },
                future.filter { it.direction == DebtDirection.RECEIVE && it.id in debtMonths }.sumOf { it.remaining },
                debts.filter { it.remaining > 0 && due(it) >= today }.minOfOrNull(::due), late.minOfOrNull(::due),
                invoices.mapNotNull { invoice -> invoice.people.firstOrNull { it.id == id }?.let { PersonInvoice(invoice, it) } }.sortedByDescending { it.invoice.month },
                debts.filter { it.purchaseId == null }.sortedWith(compareBy<DebtBalance> { it.due }.thenBy { it.id }),
            )
        }, debtMonths)
    }

    fun history(id: Long, ledger: LedgerSnapshot, rows: List<ReviewMovement>): List<PersonHistoryEntry> {
        val sources = rows.associateBy { it.id }
        val debts = ledger.debts.filter { it.personId == id }.associateBy { it.id }
        return ledger.actions.asReversed().mapNotNull { action -> when (action) {
            is LedgerAction.Payment -> if (action.personId != id) null else PersonHistoryEntry(action.date,
                if (action.direction == DebtDirection.RECEIVE) "Recebido" else "Pago à pessoa", action.allocations.values.sum(),
                action.allocations.keys.mapNotNull { debts[it]?.title }.joinToString(" · "))
            is LedgerAction.InvoiceAllocation -> action.allocations[id]?.let { cents -> PersonHistoryEntry(sources[action.cardMovementId]?.row?.date,
                "Pago ao banco", cents, "Parte na fatura de ${AppDates.month(YearMonth.from(action.reference))}") }
            is LedgerAction.Refund -> action.credits.filterKeys { it.substringAfterLast(':').toLongOrNull() == id }.values.sum().takeIf { it > 0 }?.let { cents ->
                PersonHistoryEntry(action.date, "Estorno da compra", cents, ledger.purchases.firstOrNull { it.id == action.purchaseId }?.title.orEmpty()) }
            is LedgerAction.Postpone -> debts[action.debtId]?.let { d -> PersonHistoryEntry(null, "Vencimento alterado", null, "${d.title} · ${AppDates.date(action.due)} · ${action.reason}") }
            is LedgerAction.Debt -> if (action.personId != id) null else PersonHistoryEntry(null, if (action.direction == DebtDirection.RECEIVE) "Dívida a receber registrada" else "Dívida a pagar registrada", action.cents, "${action.title} · Vencimento ${AppDates.date(action.due)}")
            else -> null
        } }
    }

    fun statement(name: String, overview: PeopleOverview, person: PersonSummary, month: YearMonth? = null): String {
        fun money(cents: Long) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents, 2))
        return buildString {
            appendLine("Meu Financeiro · $name")
            if (month == null) {
                appendLine("Saldos atuais até a fatura ${AppDates.month(overview.reference)}")
                person.invoices.filter { it.invoice.month <= overview.reference && it.share.receivable > 0 }.sortedBy { it.invoice.month }.forEach {
                    appendLine("${AppDates.month(it.invoice.month)}: ${money(it.share.receivable)} a receber")
                }
                val outside = person.outside.filter { it.due <= overview.outsideCutoff && it.direction == DebtDirection.RECEIVE }.sumOf { it.remaining }
                if (outside > 0) appendLine("Fora do cartão: ${money(outside)} a receber")
                appendLine("A receber: ${money(person.receivable)}")
                appendLine("Deste valor, vencido: ${money(person.overdue)}")
                appendLine("Eu devo: ${money(person.payable)}")
            } else {
                val entry = person.invoices.single { it.invoice.month == month }
                appendLine("Fatura ${AppDates.month(month)} · Saldos atuais")
                entry.share.items.forEach { item -> appendLine("${item.title}${item.installment?.let { " · Parcela $it" }.orEmpty()}: ${money(item.cents)}") }
                appendLine("Parte na fatura: ${money(entry.share.cents)}")
                appendLine("Recebido: ${money(entry.share.received)}")
                appendLine("A receber: ${money(entry.share.receivable)}")
            }
        }.trimEnd()
    }
}
