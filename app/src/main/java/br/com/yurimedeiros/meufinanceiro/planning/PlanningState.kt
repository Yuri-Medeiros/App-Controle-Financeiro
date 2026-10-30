package br.com.yurimedeiros.meufinanceiro.planning

import br.com.yurimedeiros.meufinanceiro.domain.*
import br.com.yurimedeiros.meufinanceiro.importing.*
import br.com.yurimedeiros.meufinanceiro.ledger.*
import java.time.LocalDate
import java.time.YearMonth

internal enum class ExpenseFrequency { ONCE, WEEKLY, MONTHLY, YEARLY }
internal data class PlannedExpense(val id: String, val title: String, val cents: Long, val firstDue: LocalDate,
    val frequency: ExpenseFrequency, val category: String, val active: Boolean = true, val confirmedDates: Map<String, LocalDate> = emptyMap(), val endDate: LocalDate? = null)
internal data class ReserveChange(val id: String, val title: String, val cents: Long, val location: ReserveLocation,
    val date: LocalDate, val cycle: String? = null)
internal data class PlanningState(
    val balanceCents: Long? = null, val balanceDate: LocalDate? = null, val salaryCents: Long = 0,
    val budgets: Map<String, Long> = emptyMap(), val salaryDates: Map<String, LocalDate> = emptyMap(),
    val fixedReserve: Long = 0, val reserveStart: String? = null,
    val reserves: List<ReserveChange> = emptyList(), val expenses: List<PlannedExpense> = emptyList(), val invoiceDates: Map<String, LocalDate> = emptyMap(),
)
internal data class ExpenseOccurrence(val expense: PlannedExpense, val nominal: LocalDate, val due: LocalDate?)
internal data class PlanningView(val available: Long?, val nextSalary: LocalDate?, val balance: Long?, val reserved: Long,
    val obligations: Long, val extraBudget: Long, val budgetRemaining: Map<String, Long>, val warnings: List<String>)
internal data class CycleProjection(val start: LocalDate, val next: LocalDate?, val available: Long?, val warnings: List<String>)

internal object PlanningRules {
    fun categoryCovers(parent: String, child: String) = child == parent || child.startsWith("$parent / ")
    fun validate(p: PlanningState, previous: PlanningState) {
        require((p.balanceCents == null) == (p.balanceDate == null))
        require(p.salaryCents >= 0 && p.fixedReserve >= 0 && p.budgets.all { it.key.isNotBlank() && it.value >= 0 })
        val categories = p.budgets.keys.toList()
        require(categories.indices.all { i -> categories.indices.all { j -> i == j || !categoryCovers(categories[i], categories[j]) } }) { "Use limite na categoria ou nas subcategorias, sem sobrepor os dois." }
        fun validDay(date: LocalDate) = date.dayOfWeek !in setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY) && (date.year != 2026 || BankCalendar.adjust(date) == date)
        p.salaryDates.forEach { (month, date) -> require(YearMonth.parse(month) == YearMonth.from(date) && validDay(date)) }
        p.invoiceDates.forEach { (month, date) -> require(YearMonth.parse(month) == YearMonth.from(date) && validDay(date)) }
        p.reserveStart?.let { YearMonth.parse(it) }
        require(p.reserves.take(previous.reserves.size) == previous.reserves) { "O histórico de reservas não pode ser reescrito." }
        require(p.reserves.map { it.id }.distinct().size == p.reserves.size)
        val balances = mutableMapOf<Pair<String, ReserveLocation>, Long>()
        val cycles = mutableSetOf<String>()
        p.reserves.forEach { r ->
            require(r.id.isNotBlank() && r.title.isNotBlank())
            val key = r.title to r.location
            balances[key] = Math.addExact(balances[key] ?: 0, r.cents)
            require(balances.getValue(key) >= 0) { "Liberação excede o valor reservado." }
            r.cycle?.let { require(cycles.add(it)); YearMonth.parse(it) }
        }
        require(p.expenses.map { it.id }.distinct().size == p.expenses.size)
        require(p.expenses.all { it.id.isNotBlank() && it.title.isNotBlank() && it.category.isNotBlank() && it.cents > 0 })
        previous.expenses.forEach { old ->
            val new = p.expenses.single { it.id == old.id }
            require(new.copy(active = old.active, confirmedDates = old.confirmedDates, endDate = old.endDate) == old) { "Cadastre uma nova previsão para alterar frequência ou valores; preserve o histórico anterior." }
        }
    }
    fun occurrences(expense: PlannedExpense, until: LocalDate): List<ExpenseOccurrence> {
        if (!expense.active && expense.endDate == null) return emptyList()
        val end = if (expense.endDate != null && expense.endDate < until) expense.endDate else until
        val result = mutableListOf<ExpenseOccurrence>()
        for (index in 0..2400) {
            val date = when (expense.frequency) {
                ExpenseFrequency.ONCE -> if (index == 0) expense.firstDue else break
                ExpenseFrequency.WEEKLY -> expense.firstDue.plusWeeks(index.toLong())
                ExpenseFrequency.MONTHLY -> expense.firstDue.plusMonths(index.toLong())
                ExpenseFrequency.YEARLY -> expense.firstDue.plusYears(index.toLong())
            }
            if (date > end) break
            result += ExpenseOccurrence(expense, date, expense.confirmedDates[date.toString()] ?: BankCalendar.adjust(date))
        }
        return result
    }
    fun salary(p: PlanningState, month: YearMonth): LocalDate? = p.salaryDates[month.toString()] ?: BankCalendar.adjust(month.atDay(5))
    fun cycle(p: PlanningState, today: LocalDate): SalaryCycle? {
        val month = YearMonth.from(today)
        val current = salary(p, month) ?: return null
        val startMonth = if (today >= current) month else month.minusMonths(1)
        val start = salary(p, startMonth) ?: return null
        val next = salary(p, startMonth.plusMonths(1)) ?: return null
        return SalaryCycle(start, next)
    }
    fun pendingReserve(p: PlanningState, today: LocalDate): List<ReserveChange> {
        if (p.fixedReserve <= 0 || p.reserveStart == null) return emptyList()
        val cycle = cycle(p, today) ?: return emptyList()
        val last = YearMonth.from(cycle.start)
        var month = YearMonth.parse(p.reserveStart)
        val used = p.reserves.mapNotNull { it.cycle }.toSet()
        val changes = mutableListOf<ReserveChange>()
        repeat(240) {
            if (month > last) return changes
            val date = salary(p, month) ?: return changes
            if (month.toString() !in used) changes += ReserveChange("cycle:$month", "Reserva do ciclo", p.fixedReserve, ReserveLocation.BANK, date, month.toString())
            month = month.plusMonths(1)
        }
        return changes
    }
    fun view(p: PlanningState, ledger: LedgerSnapshot, rows: List<ReviewMovement>, today: LocalDate, projecting: Boolean = false): PlanningView {
        val warnings = mutableListOf<String>()
        val cycle = cycle(p, today)
        if (cycle == null) warnings += "Confirme as datas salariais: calendário ainda não validado para este período."
        if (p.balanceCents == null || p.balanceDate == null) warnings += "Informe o saldo bancário ao fim de uma data de referência."
        if (p.budgets.isEmpty()) warnings += "Defina os limites por categoria para incluir o orçamento no disponível."
        val relevantAccount = rows.filter { it.row.kind == DocumentKind.NUBANK_ACCOUNT && p.balanceDate?.let { base -> it.row.date > base && it.row.date <= today } == true && it.status != "IGNORED" }
        if (relevantAccount.any { it.status == "CONFLICT" }) warnings += "Há divergências no extrato após a data do saldo."
        if (p.balanceDate != null && p.balanceDate >= today) warnings += "Use o saldo fechado de um dia anterior para aplicar corretamente as movimentações seguintes."
        val balance = if (relevantAccount.any { it.status == "CONFLICT" } || p.balanceDate?.let { it >= today } == true) null else p.balanceCents?.let { base -> relevantAccount.fold(base) { sum, m -> Math.addExact(sum, m.row.cents) } }
        val reserves = (p.reserves + pendingReserve(p, today)).filter { it.location == ReserveLocation.BANK }.sumOf { it.cents }
        val next = cycle?.next
        val obligations = mutableListOf<AvailableObligation>()
        val matched = ledger.actions.filterIsInstance<LedgerAction.ForecastMatch>().map { it.expenseId to it.nominal }.toSet()
        val occurrences = p.expenses.flatMap { occurrences(it, next?.minusDays(1) ?: today) }.filter { (it.expense.id to it.nominal) !in matched }
        occurrences.forEach { item ->
            if (item.due == null) warnings += "Confirme o vencimento de ${item.expense.title}: calendário não validado."
            else if (next != null && item.due < next && (!projecting || item.due >= checkNotNull(cycle).start)) obligations += AvailableObligation("forecast:${item.expense.id}:${item.nominal}", item.expense.cents, item.due)
        }
        ledger.debts.filter { it.direction == DebtDirection.PAY && it.remaining > 0 && (!projecting || (cycle != null && it.due >= cycle.start)) }.forEach { obligations += AvailableObligation("person:${it.id}", it.remaining, it.due) }
        val importedCard = rows.filter { it.row.kind == DocumentKind.NUBANK_CARD && it.status !in setOf("CONFLICT", "IGNORED") }
        if (rows.any { it.row.kind == DocumentKind.NUBANK_CARD && it.status == "CONFLICT" }) warnings += "Há divergências na fatura."
        val allMonths = (importedCard.mapNotNull { it.row.statement?.let(YearMonth::from) } + ledger.purchases.flatMap { p -> p.parts.map { YearMonth.from(it.due) } }).distinct()
        allMonths.forEach { month ->
            val projected = ledger.purchases.flatMap { plan -> plan.parts.filter { it.movementId == 0L && YearMonth.from(it.due) == month }.map { plan to it } }
            val imported = importedCard.filter { it.row.statement?.let(YearMonth::from) == month }
            if (projected.isNotEmpty() && imported.any { it.row.cents > 0 && it.id !in ledger.usedMovementIds }) warnings += "Vincule as parcelas de $month para evitar contar importação e previsão juntas."
            val due = p.invoiceDates[month.toString()] ?: ledger.purchases.flatMap { it.parts }.filter { YearMonth.from(it.due) == month }.map { it.due }.distinct().singleOrNull() ?: BankCalendar.adjust(month.atDay(8))
            if (due == null) {
                if (next != null && month <= YearMonth.from(next)) warnings += "Confirme o vencimento da fatura de $month."
            } else if (next != null && due < next && (!projecting || due >= checkNotNull(cycle).start)) {
                if (due in cycle && imported.any { it.row.cents > 0 && it.id !in ledger.usedMovementIds }) warnings += "Confirme os responsáveis das compras de $month para fechar o orçamento sem dupla contagem."
                val charges = imported.filter { !PaymentMatching.isPayment(it.row) }.sumOf { it.row.cents } + projected.sumOf { it.second.cents }
                val paid = imported.filter { it.status == "PAYMENT" && rows.any { account -> account.id == it.pairedMovementId && account.row.date <= today } }.sumOf { -it.row.cents }
                obligations += AvailableObligation("invoice:$month", (charges - paid).coerceAtLeast(0), due)
            }
        }
        val refunds = ledger.actions.filterIsInstance<LedgerAction.Refund>()
        val spent = mutableMapOf<String, Long>()
        if (cycle != null) {
            ledger.purchases.forEach { plan -> plan.parts.filter { it.due in cycle }.forEach { part ->
                val own = (plan.shares[0L]?.get(part.number - 1) ?: 0) - refunds.sumOf { it.credits["${plan.id}:${part.number}:0"] ?: 0 }
                spent[plan.category] = (spent[plan.category] ?: 0) + own
            } }
            rows.filter { it.row.kind == DocumentKind.NUBANK_ACCOUNT && it.row.cents < 0 && it.row.date in cycle && it.status == "CONFIRMED" && it.category.isNotBlank() && !PaymentMatching.isPayment(it.row) }.forEach { spent[it.category] = (spent[it.category] ?: 0) - it.row.cents }
        }
        val remaining = p.budgets.mapValues { (category, limit) -> (limit - spent.filterKeys { categoryCovers(category, it) }.values.sum()).coerceAtLeast(0) }
        val needs = remaining.map { (category, amount) -> BudgetNeed(amount, occurrences.filter { categoryCovers(category, it.expense.category) && it.due != null && next != null && it.due < next && it.due >= checkNotNull(cycle).start }.sumOf { it.expense.cents }) }
        val extra = needs.sumOf { (it.remainingCents - it.coveredByObligationsCents).coerceAtLeast(0) }
        val available = if (warnings.isEmpty() && balance != null && next != null) FinancialPlanning.available(balance, today, next, obligations, listOf(AvailableReserve(reserves, ReserveLocation.BANK)), needs) else null
        return PlanningView(available, next, balance, reserves, obligations.filter { next != null && it.due < next }.sumOf { it.cents }, extra, remaining, warnings.distinct())
    }
    fun project(p: PlanningState, ledger: LedgerSnapshot, rows: List<ReviewMovement>, today: LocalDate): List<CycleProjection> {
        if (p.salaryCents <= 0) return emptyList()
        var previous = view(p, ledger, rows, today)
        val result = mutableListOf<CycleProjection>()
        repeat(3) {
            val start = previous.nextSalary ?: return result
            val carried = previous.available ?: return result
            val forecast = p.copy(
                balanceCents = Math.addExact(carried, p.salaryCents), balanceDate = start.minusDays(1),
                fixedReserve = 0, reserveStart = null,
                reserves = if (p.fixedReserve > 0) listOf(ReserveChange("projection:$start", "Reserva prevista", p.fixedReserve, ReserveLocation.BANK, start)) else emptyList(),
            )
            val next = view(forecast, ledger, rows.filter { it.row.date <= today }, start, projecting = true)
            result += CycleProjection(start, next.nextSalary, next.available, next.warnings)
            previous = next
        }
        return result
    }

}
