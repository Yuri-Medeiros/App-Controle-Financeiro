package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.importing.*
import br.com.yurimedeiros.meufinanceiro.planning.InvoiceDueCalendar
import java.time.LocalDate
import java.time.YearMonth

internal data class InvoiceItem(val title: String, val cents: Long, val date: LocalDate?, val installment: String? = null, val movementId: Long = 0)
internal data class InvoicePerson(val id: Long, val cents: Long, val received: Long, val receivable: Long, val bankPaid: Long, val items: List<InvoiceItem>) {
    val bankCapacity get() = (items.filter { it.cents > 0 }.sumOf { it.cents } - bankPaid).coerceAtLeast(0)
}
internal data class Invoice(
    val month: YearMonth, val due: LocalDate?, val total: Long, val paid: Long,
    val people: List<InvoicePerson>, val payments: List<ReviewMovement>,
    val imported: Boolean, val warnings: List<String>,
) {
    val remaining get() = (total - paid).coerceAtLeast(0)
    val unallocated get() = paid - people.sumOf { it.bankPaid }
}

/** Read-only projection: never infers family receipts or a personal owner from a category. */
internal object InvoiceBook {
    const val UNASSIGNED = -1L
    private fun month(part: SchedulePart, rows: Map<Long, ReviewMovement>) =
        rows[part.movementId]?.row?.statement?.let(YearMonth::from) ?: YearMonth.from(part.due)

    fun debtMonth(debt: DebtBalance, ledger: LedgerSnapshot, rows: List<ReviewMovement>): YearMonth? {
        val part = ledger.purchases.firstOrNull { it.id == debt.purchaseId }?.parts?.firstOrNull { it.number == debt.number } ?: return null
        return month(part, rows.associateBy { it.id })
    }

    fun invoices(ledger: LedgerSnapshot, rows: List<ReviewMovement>): List<Invoice> {
        val byId = rows.associateBy { it.id }
        val automatic = AutomaticInstallments.forecasts(ledger, rows)
        val card = rows.filter { it.row.kind == DocumentKind.NUBANK_CARD && it.status != "IGNORED" }
        val partMonths = ledger.purchases.flatMap { p -> p.parts.map { (p.id to it.number) to month(it, byId) } }.toMap()
        val debtsByMonth = ledger.debts.filter { it.direction == DebtDirection.RECEIVE }.groupBy { partMonths[it.purchaseId to it.number] }
        val months = (automatic.keys + card.mapNotNull { it.row.statement?.let(YearMonth::from) } + ledger.purchases.flatMap { p -> p.parts.map { month(it, byId) } }).distinct().sorted()
        return months.map { m ->
            val imported = card.filter { it.row.statement?.let(YearMonth::from) == m }
            val valid = imported.filter { it.status != "CONFLICT" }
            val parts = ledger.purchases.flatMap { p -> p.parts.filter { month(it, byId) == m }.map { p to it } }
            val linked = parts.map { it.second.movementId }.filter { it > 0 }.toSet()
            val items = linkedMapOf<Long, MutableList<InvoiceItem>>()
            fun item(id: Long, value: InvoiceItem) { if (value.cents != 0L) items.getOrPut(id) { mutableListOf() }.add(value) }
            parts.forEach { (p, part) ->
                p.shares.forEach { (id, amounts) -> item(id, InvoiceItem(byId[part.movementId]?.friendlyName?.ifBlank { p.title } ?: p.title, amounts[part.number - 1], byId[part.movementId]?.row?.date, "${part.number}/${p.parts.size}", movementId = part.movementId)) }
            }
            valid.filter { !PaymentMatching.isPayment(it.row) && it.id !in linked }.forEach { row ->
                val refund = ledger.actions.filterIsInstance<LedgerAction.Refund>().firstOrNull { it.movementId == row.id }
                if (refund == null) item(UNASSIGNED, InvoiceItem(row.friendlyName.ifBlank { row.row.description }, row.row.cents, row.row.date, movementId = row.id))
                else refund.credits.forEach { (key, cents) -> item(key.substringAfterLast(':').toLong(), InvoiceItem("Estorno · ${row.friendlyName.ifBlank { row.row.description }}", -cents, row.row.date, movementId = row.id)) }
            }
            automatic[m].orEmpty().forEach { item(UNASSIGNED, it) }
            val payments = valid.filter { c -> PaymentMatching.isPayment(c.row) && c.status == "PAYMENT" &&
                byId[c.pairedMovementId]?.let { a -> a.pairedMovementId == c.id && a.status == "PAYMENT" && a.row.kind == DocumentKind.NUBANK_ACCOUNT && a.row.cents == c.row.cents } == true }
            val allocations = ledger.actions.filterIsInstance<LedgerAction.InvoiceAllocation>().filter { a -> payments.any { it.id == a.cardMovementId } }
            val debts = debtsByMonth[m].orEmpty()
            val ids = (items.keys + allocations.flatMap { it.allocations.keys }).distinct().sortedWith(compareBy<Long> { if (it == UNASSIGNED) Long.MAX_VALUE else it })
            val people = ids.map { id ->
                val owned = items[id].orEmpty()
                InvoicePerson(id, owned.sumOf { it.cents }, debts.filter { it.personId == id }.sumOf { it.paidCents },
                    debts.filter { it.personId == id }.sumOf { it.remaining }, allocations.sumOf { it.allocations[id] ?: 0 }, owned)
            }
            val due = InvoiceDueCalendar.due(m, ledger.planning.invoiceDates[m.toString()])
            val warnings = buildList {
                if (imported.any { it.status == "CONFLICT" }) add("Há divergências de importação. Revise em Extratos.")
                if (parts.any { it.second.movementId == 0L } && valid.any { it.row.cents > 0 && it.id !in linked }) add("Vincule as parcelas importadas para não contar previsão e compra duas vezes.")
                if (valid.any { PaymentMatching.isPayment(it.row) && it !in payments }) add("Há pagamento aguardando conciliação com o extrato.")
            }
            Invoice(m, due, people.sumOf { it.cents }, payments.sumOf { -it.row.cents }, people, payments, imported.isNotEmpty(), warnings)
        }
    }

    fun defaultMonth(invoices: List<Invoice>, today: LocalDate): YearMonth? =
        invoices.firstOrNull { it.remaining > 0 }?.month ?: invoices.lastOrNull { it.month <= YearMonth.from(today) }?.month ?: invoices.firstOrNull()?.month

    fun previousDebts(invoice: Invoice, invoices: List<Invoice>, ledger: LedgerSnapshot, rows: List<ReviewMovement>): List<DebtBalance> {
        val byId = rows.associateBy { it.id }
        val partMonths = ledger.purchases.flatMap { p -> p.parts.map { (p.id to it.number) to month(it, byId) } }.toMap()
        val settled = invoices.filter { it.month < invoice.month && it.imported && it.remaining == 0L && it.warnings.isEmpty() }.map { it.month }.toSet()
        return ledger.debts.filter { it.direction == DebtDirection.RECEIVE && it.remaining > 0 && partMonths[it.purchaseId to it.number] in settled }
            .sortedWith(compareBy<DebtBalance> { it.due }.thenBy { it.id })
    }

    /** Receipts for THIS invoice first. The user's advance covers only a known own share. */
    fun suggestBankAllocation(invoice: Invoice, cents: Long): Map<Long, Long> {
        require(cents > 0)
        var left = cents
        val result = linkedMapOf<Long, Long>()
        invoice.people.filter { it.id > 0 }.forEach { p ->
            val available = minOf((p.received - p.bankPaid).coerceAtLeast(0), (p.cents - p.bankPaid).coerceAtLeast(0), left)
            if (available > 0) { result[p.id] = available; left -= available }
        }
        invoice.people.firstOrNull { it.id == 0L }?.let { p ->
            val own = minOf(left, (p.cents - p.bankPaid).coerceAtLeast(0))
            if (own > 0) result[0] = own
        }
        return result // Any remainder must be assigned explicitly, never guessed.
    }

    fun suggestReceipt(debts: List<DebtBalance>, cents: Long): Map<String, Long> {
        require(cents > 0)
        var left = cents
        return buildMap {
            debts.sortedWith(compareBy<DebtBalance> { it.due }.thenBy { it.id }).forEach { d ->
                val amount = minOf(left, d.remaining)
                if (amount > 0) { put(d.id, amount); left -= amount }
            }
        }
    }

    fun validateAllocation(action: LedgerAction.InvoiceAllocation, ledger: LedgerSnapshot, rows: List<ReviewMovement>) {
        val card = rows.single { it.id == action.cardMovementId }
        val account = rows.single { it.id == action.accountMovementId }
        require(card.row.kind == DocumentKind.NUBANK_CARD && account.row.kind == DocumentKind.NUBANK_ACCOUNT)
        require(PaymentMatching.isPayment(card.row) && PaymentMatching.isPayment(account.row))
        require(card.status == "PAYMENT" && account.status == "PAYMENT" && card.pairedMovementId == account.id && account.pairedMovementId == card.id) { "Concilie o pagamento com o extrato primeiro." }
        require(card.row.statement == action.reference && card.row.cents == account.row.cents)
        require(action.allocations.values.fold(0L, Math::addExact) == -card.row.cents) { "Distribua o valor integral do pagamento." }
        val invoice = invoices(ledger, rows).single { it.month == YearMonth.from(action.reference) }
        action.allocations.forEach { (id, cents) ->
            val person = invoice.people.singleOrNull { it.id == id }
            require(person != null && cents <= person.bankCapacity) { "O valor excede a parte desta pessoa na fatura." }
        }
    }
}
