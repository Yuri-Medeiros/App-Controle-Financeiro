package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.domain.ReserveLocation
import br.com.yurimedeiros.meufinanceiro.importing.DocumentKind
import br.com.yurimedeiros.meufinanceiro.importing.PaymentMatching
import br.com.yurimedeiros.meufinanceiro.ledger.*
import br.com.yurimedeiros.meufinanceiro.planning.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

private fun planningAmount(cents: Long?) = cents?.let { BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',') }.orEmpty()
private fun signedAmount(value: String): Long = if (value.trim().startsWith('-')) -FinanceLedger.money(value.trim().removePrefix("-")) else FinanceLedger.money(value)
private fun frequencyLabel(f: ExpenseFrequency) = when (f) { ExpenseFrequency.ONCE -> "Uma vez"; ExpenseFrequency.WEEKLY -> "Semanal"; ExpenseFrequency.MONTHLY -> "Mensal"; ExpenseFrequency.YEARLY -> "Anual" }

@Composable
internal fun PlanningScreen(state: FinanceUiState, model: FinanceViewModel) {
    val p = state.ledger.planning
    val today = LocalDate.now()
    val view = PlanningRules.view(p, state.ledger, state.movements, today)
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var match by remember { mutableStateOf<ExpenseOccurrence?>(null) }
    var dateConfirmation by remember { mutableStateOf<ExpenseOccurrence?>(null) }
    var confirmedDate by remember { mutableStateOf("") }
    var matchingId by remember { mutableLongStateOf(0) }
    GlassPanel(highlighted = true) {
        Text("Até o próximo salário", style = MaterialTheme.typography.titleSmall)
        Text(view.available?.let(::ledgerMoney) ?: "—", style = MaterialTheme.typography.headlineLarge, color = if ((view.available ?: 0) < 0) ExpenseColor else MaterialTheme.colorScheme.onSurface)
        Text(view.nextSalary?.let { "Antes de $it" } ?: "Datas a confirmar", style = MaterialTheme.typography.bodySmall)
        if (view.warnings.isNotEmpty()) Text(view.warnings.first(), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { dialog = "settings" }) { Text("Configurar saldo e salário") }
    }
    PillTabs(listOf("Orçamento", "Previsões", "Reservas"), tab) { tab = it }
    when (tab) {
        0 -> {
            OutlinedButton(onClick = { dialog = "budget" }) { Text("Definir limite por categoria") }
            if (p.budgets.isEmpty()) Text("Nenhum limite configurado.")
            view.budgetRemaining.forEach { (category, cents) -> GlassPanel {
                Text(category, style = MaterialTheme.typography.titleSmall)
                Text("Restante · ${ledgerMoney(cents)}")
                Text("Limite por ciclo · ${ledgerMoney(p.budgets.getValue(category))}", style = MaterialTheme.typography.bodySmall)
            } }
            Text("Os limites renovam por ciclo e não acumulam sobras. Só sua parte das parcelas consome o orçamento no ciclo do vencimento.", style = MaterialTheme.typography.bodySmall)
        }
        1 -> {
            OutlinedButton(onClick = { dialog = "expense" }) { Text("Adicionar previsão") }
            val matched = state.ledger.actions.filterIsInstance<LedgerAction.ForecastMatch>().map { it.expenseId to it.nominal }.toSet()
            val occurrences = p.expenses.flatMap { PlanningRules.occurrences(it, view.nextSalary?.plusMonths(2) ?: today.plusMonths(2)) }.filter { (it.expense.id to it.nominal) !in matched }.sortedBy { it.nominal }
            if (occurrences.isEmpty()) Text("Nenhuma previsão em aberto.")
            occurrences.take(60).forEach { occurrence -> GlassPanel {
                Text(occurrence.expense.title, style = MaterialTheme.typography.titleSmall)
                Text("${occurrence.due ?: "Data a confirmar"} · ${ledgerMoney(occurrence.expense.cents)}")
                Row {
                    TextButton(onClick = { match = occurrence; matchingId = 0 }) { Text("Vincular ao extrato") }
                    TextButton(onClick = { dateConfirmation = occurrence; confirmedDate = (occurrence.due ?: occurrence.nominal).toString() }) { Text("Vencimento") }
                }
            } }
            p.expenses.filter { it.active }.forEach { expense -> TextButton(onClick = {
                model.saveLedger(LedgerAction.Planning(p.copy(expenses = p.expenses.map { if (it.id == expense.id) it.copy(active = false, endDate = today) else it }))) {}
            }, enabled = !state.syncing) { Text("Encerrar previsão · ${expense.title}") } }
        }
        2 -> {
            Text("Mantidas na conta · ${ledgerMoney(view.reserved)}", style = MaterialTheme.typography.titleMedium)
            val invested = p.reserves.filter { it.location == ReserveLocation.INVESTMENT }.sumOf { it.cents }
            Text("Em investimentos · ${ledgerMoney(invested)}")
            OutlinedButton(onClick = { dialog = "reserve" }) { Text("Reservar ou liberar") }
            Text("Reserva fixa por ciclo · ${ledgerMoney(p.fixedReserve)}", style = MaterialTheme.typography.bodySmall)
            Text("Reservas acumulam até o uso ou liberação. Reservar não transfere dinheiro nem realiza um aporte.", style = MaterialTheme.typography.bodySmall)
            p.reserves.takeLast(20).reversed().forEach { Text("${it.date} · ${it.title} · ${ledgerMoney(it.cents)}", style = MaterialTheme.typography.bodySmall) }
        }
    }
    val projections = PlanningRules.project(p, state.ledger, state.movements, today)
    if (projections.isNotEmpty()) GlassPanel {
        Text("Próximos ciclos", style = MaterialTheme.typography.titleSmall)
        projections.forEach { future ->
            Text("${future.start} · ${future.available?.let(::ledgerMoney) ?: "Datas ou vínculos a confirmar"}", color = if ((future.available ?: 0) < 0) ExpenseColor else MaterialTheme.colorScheme.onSurface)
        }
        Text("Projeção conservadora: considera salário futuro apenas no respectivo ciclo, uso dos limites e novas reservas. Não antecipa recebimentos de familiares.", style = MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick = { dialog = "calculation" }) { Text("Como o disponível é calculado") }
    if (dialog == "calculation") LedgerDialog("Seu disponível", state, { dialog = "" }) {
        Text("Saldo bancário ${view.balance?.let(::ledgerMoney) ?: "a confirmar"}")
        Text("− Obrigações ${ledgerMoney(view.obligations)}\n− Reservas na conta ${ledgerMoney(view.reserved)}\n− Orçamento adicional ${ledgerMoney(view.extraBudget)}")
        Text("Flash, investimentos e recebimentos familiares futuros não são somados ao dinheiro disponível. O próximo salário pertence ao próximo ciclo; valores negativos continuam visíveis.")
        view.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Calendário Ribeirão Preto / bancário: ano 2026, consultado ${BankCalendar.SOURCE_DATE}. Anos não validados exigem confirmação das datas. Pontos facultativos municipais não são tratados automaticamente como feriados.", style = MaterialTheme.typography.bodySmall)
        Text("Fontes: ${BankCalendar.MUNICIPAL_SOURCE}\n${BankCalendar.BANK_SOURCE}", style = MaterialTheme.typography.bodySmall)
    }
    if (dialog == "settings") {
        var balance by remember { mutableStateOf(planningAmount(p.balanceCents)) }
        var date by remember { mutableStateOf((p.balanceDate ?: today.minusDays(1)).toString()) }
        var salary by remember { mutableStateOf(planningAmount(p.salaryCents)) }
        var fixed by remember { mutableStateOf(planningAmount(p.fixedReserve)) }
        var salaryDate by remember { mutableStateOf("") }
        var invoiceDate by remember { mutableStateOf("") }
        LedgerDialog("Saldo, salário e ciclo", state, { dialog = ""; error = null }, confirm = {
            try {
                val fixedValue = FinanceLedger.money(fixed)
                val salaryDates = p.salaryDates.toMutableMap()
                if (salaryDate.isNotBlank()) LocalDate.parse(salaryDate).let { salaryDates[YearMonth.from(it).toString()] = it }
                val invoices = p.invoiceDates.toMutableMap()
                if (invoiceDate.isNotBlank()) LocalDate.parse(invoiceDate).let { invoices[YearMonth.from(it).toString()] = it }
                val start = if (fixedValue != p.fixedReserve && fixedValue > 0) YearMonth.from(PlanningRules.cycle(p.copy(salaryDates = salaryDates), today)?.start ?: today).toString() else p.reserveStart
                model.saveLedger(LedgerAction.Planning(p.copy(balanceCents = signedAmount(balance), balanceDate = LocalDate.parse(date), salaryCents = FinanceLedger.money(salary), fixedReserve = fixedValue, reserveStart = start, salaryDates = salaryDates, invoiceDates = invoices))) { dialog = "" }
            } catch (_: Exception) { error = "Revise os valores e as datas." }
        }) {
            LedgerField("Saldo bancário ao fim da data R$", balance) { balance = it }
            LedgerField("Data do saldo AAAA-MM-DD", date) { date = it }
            Text("Somente movimentações após essa data alteram o saldo. Confira se os extratos desse período estão completos.", style = MaterialTheme.typography.bodySmall)
            LedgerField("Salário previsto R$", salary) { salary = it }
            LedgerField("Reserva fixa por ciclo R$", fixed) { fixed = it }
            Text("A reserva é somada uma vez em cada ciclo a partir da configuração. Para ajustar o ciclo atual, use Reservar ou liberar.", style = MaterialTheme.typography.bodySmall)
            LedgerField("Confirmar salário de um mês (opcional)", salaryDate) { salaryDate = it }
            LedgerField("Confirmar fatura de um mês (opcional)", invoiceDate) { invoiceDate = it }
            Text("Datas AAAA-MM-DD. Por padrão: salário dia 5 e fatura dia 8, avançando ao dia útil validado.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (dialog == "budget") {
        var category by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
        LedgerDialog("Limite por ciclo", state, { dialog = ""; error = null }, confirm = {
            try { require(category.isNotBlank()); model.saveLedger(LedgerAction.Planning(p.copy(budgets = p.budgets + (category.trim() to FinanceLedger.money(amount))))) { dialog = "" } }
            catch (_: Exception) { error = "Informe categoria e valor." }
        }) {
            CategoryDropdown(category, { category = it }, !state.syncing); LedgerField("Limite R$", amount) { amount = it }
            Text("O limite da categoria inclui suas subcategorias. Evite limites sobrepostos entre a categoria e seus filhos. O histórico de configuração é preservado.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (dialog == "reserve") {
        var title by remember { mutableStateOf("Reserva do ciclo") }; var amount by remember { mutableStateOf("") }
        var location by remember { mutableStateOf(ReserveLocation.BANK) }
        val id = remember { UUID.randomUUID().toString() }
        LedgerDialog("Reservar ou liberar", state, { dialog = ""; error = null }, confirm = {
            try { model.saveLedger(LedgerAction.Planning(p.copy(reserves = p.reserves + ReserveChange(id, title.trim(), signedAmount(amount), location, today)))) { dialog = "" } }
            catch (_: Exception) { error = "Revise nome e valor. Use sinal negativo para liberar." }
        }) {
            LedgerField("Nome da reserva", title) { title = it }; LedgerField("Valor R$ (negativo para liberar)", amount) { amount = it }
            LedgerChoice("Local", if (location == ReserveLocation.BANK) "Na conta" else "Em investimentos", ReserveLocation.entries.map { l -> (if (l == ReserveLocation.BANK) "Na conta" else "Em investimentos") to { location = l } })
            Text("Para liberar, use exatamente o nome e o local da reserva existente. A liberação não realiza resgate bancário.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (dialog == "expense") {
        var title by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var category by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(today.toString()) }; var frequency by remember { mutableStateOf(ExpenseFrequency.ONCE) }
        val id = remember { UUID.randomUUID().toString() }
        LedgerDialog("Nova previsão", state, { dialog = ""; error = null }, confirm = {
            try { model.saveLedger(LedgerAction.Planning(p.copy(expenses = p.expenses + PlannedExpense(id, title.trim(), FinanceLedger.money(amount), LocalDate.parse(date), frequency, category.trim())))) { dialog = "" } }
            catch (_: Exception) { error = "Revise descrição, categoria, valor e data." }
        }) {
            LedgerField("Descrição", title) { title = it }; CategoryDropdown(category, { category = it }, !state.syncing)
            LedgerField("Valor previsto R$", amount) { amount = it }; LedgerField("Primeiro vencimento AAAA-MM-DD", date) { date = it }
            LedgerChoice("Frequência", frequencyLabel(frequency), ExpenseFrequency.entries.map { f -> frequencyLabel(f) to { frequency = f } })
            Text("A previsão só deixa de ser obrigação ao vincular o lançamento real. Diferenças de valor são confirmadas nesse vínculo.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    dateConfirmation?.let { occurrence -> LedgerDialog("Confirmar vencimento", state, { dateConfirmation = null; error = null }, confirm = {
        try {
            val date = LocalDate.parse(confirmedDate)
            val expenses = p.expenses.map { if (it.id != occurrence.expense.id) it else it.copy(confirmedDates = it.confirmedDates + (occurrence.nominal.toString() to date)) }
            model.saveLedger(LedgerAction.Planning(p.copy(expenses = expenses))) { dateConfirmation = null }
        } catch (_: Exception) { error = "Informe uma data válida." }
    }) {
        Text(occurrence.expense.title)
        LedgerField("Vencimento AAAA-MM-DD", confirmedDate) { confirmedDate = it }
        Text("A confirmação vale apenas para esta ocorrência e fica no histórico.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }
    match?.let { occurrence ->
        val sources = state.movements.filter { it.id !in state.ledger.usedMovementIds && it.status !in setOf("CONFLICT", "IGNORED") && it.row.kind == DocumentKind.NUBANK_ACCOUNT && it.row.cents < 0 && !PaymentMatching.isPayment(it.row) }
        val chosen = sources.firstOrNull { it.id == matchingId }
        LedgerDialog("Confirmar lançamento real", state, { match = null; error = null }, confirm = {
            if (chosen != null) model.saveLedger(LedgerAction.ForecastMatch(occurrence.expense.id, occurrence.nominal, chosen.id)) { match = null }
            else error = "Selecione uma saída no extrato."
        }) {
            Text("${occurrence.expense.title} · previsto ${ledgerMoney(occurrence.expense.cents)}")
            LedgerChoice("Lançamento real", chosen?.let { "${it.row.date} · ${ledgerMoney(-it.row.cents)}" } ?: "Escolher", sources.map { source -> "${source.row.date} · ${ledgerMoney(-source.row.cents)} · ${source.row.description}" to { matchingId = source.id } })
            Text("O valor real do extrato prevalece. A previsão vinculada não será descontada novamente.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
