package br.com.yurimedeiros.meufinanceiro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.yurimedeiros.meufinanceiro.formatting.AppDates
import br.com.yurimedeiros.meufinanceiro.ledger.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val PeopleOverdueColor = Color(0xFFF5BA79)
internal fun peopleMonth(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.forLanguageTag("pt-BR"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) }
private enum class PeopleFilter(val label: String) { ALL("Todas"), RECEIVE("A receber"), PAY("Eu devo"), CLEAR("Sem pendências") }

@Composable
internal fun PeopleScreen(state: FinanceUiState, model: FinanceViewModel, onRouteChanged: () -> Unit = {}) {
    val today = LocalDate.now()
    val invoices = remember(state.ledger, state.movements) { InvoiceBook.invoices(state.ledger, state.movements) }
    var reference by rememberSaveable { mutableStateOf<String?>(null) }
    val month = reference?.let(YearMonth::parse) ?: InvoiceBook.defaultMonth(invoices, today) ?: YearMonth.from(today)
    val overview = remember(state.ledger, state.movements, state.people, invoices, month, today) {
        PeopleInsights.overview(state.ledger, state.movements, invoices, state.people.map { it.id }, month, today)
    }
    val months = remember(invoices, state.ledger.debts, month) { (invoices.map { it.month } + state.ledger.debts.map { YearMonth.from(it.due) } + month).distinct().sortedDescending() }
    var selectedPerson by rememberSaveable { mutableStateOf<Long?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(PeopleFilter.ALL) }
    var dialog by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val interactive = LocalFinanceInteractive.current
    fun changeReference(next: YearMonth) { reference = next.toString(); onRouteChanged() }
    fun closePerson() { selectedPerson = null; onRouteChanged() }
    BackHandler(enabled = selectedPerson != null && interactive) { closePerson() }
    selectedPerson?.let { id ->
        key(id) { PersonFinances(id, state, model, overview, months, ::changeReference, ::closePerson) }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Pessoas", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        AssetInfoButton("Informações de Pessoas") {
            Text("Saldos atuais até a fatura selecionada. Vencidos já fazem parte de A receber.")
            Text("Fora do cartão: vencimentos até ${AppDates.date(overview.outsideCutoff)}.")
            Text("Parcelas futuras ficam separadas. Receber e pagar não se compensam.")
            Text("Só são considerados saldos familiares conciliados; não se presumem pagamentos históricos.")
        }
    }
    PeopleReference(month, months, state, ::changeReference)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PeopleMetric("A receber", overview.receivable)
            if (overview.overdue > 0) Text("${ledgerMoney(overview.overdue)} vencidos", style = MaterialTheme.typography.bodySmall, color = PeopleOverdueColor)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PeopleMetric("Eu devo", overview.payable)
            val count = overview.people.count { it.payable > 0 }
            if (count > 0) Text("$count ${if (count == 1) "pessoa" else "pessoas"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val future = overview.people.flatMap { it.invoices }.filter { it.invoice.month > month }
    if (future.isNotEmpty()) PeopleShortcut("Próximas parcelas", "Saldo a receber nas próximas faturas", ledgerMoney(overview.futureInstallments)) { dialog = "future" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(search, { search = it }, Modifier.weight(1f), placeholder = { Text("Buscar pessoa") }, shape = FinanceInputShape, singleLine = true)
        IconButton(onClick = { name = ""; dialog = "add" }, enabled = !state.syncing, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .08f), CircleShape)) {
            Icon(AddAssetIcon, "Adicionar pessoa", tint = MaterialTheme.colorScheme.primary)
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PeopleFilter.entries.forEach { option -> FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) }) }
    }
    state.error?.takeIf { dialog.isBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val names = state.people.associate { it.id to it.name }
    val visible = overview.people.filter { person ->
        names[person.id].orEmpty().contains(search.trim(), ignoreCase = true) && when (filter) {
            PeopleFilter.ALL -> true
            PeopleFilter.RECEIVE -> person.receivable > 0 || person.futureReceivable > 0
            PeopleFilter.PAY -> person.payable > 0 || person.futurePayable > 0
            PeopleFilter.CLEAR -> person.clear
        }
    }.sortedWith(compareByDescending<PersonSummary> { it.overdue > 0 }.thenBy { it.oldestOverdue ?: LocalDate.MAX }
        .thenByDescending { it.overdue }.thenBy { it.nextDue ?: LocalDate.MAX }.thenBy { names[it.id].orEmpty().lowercase(Locale.forLanguageTag("pt-BR")) })
    GlassPanel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Seus contatos", style = MaterialTheme.typography.titleMedium)
            Text("${visible.size} ${if (visible.size == 1) "pessoa" else "pessoas"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (visible.isEmpty()) Text(if (state.people.isEmpty()) "Nenhuma pessoa cadastrada" else "Nenhuma pessoa encontrada", color = MaterialTheme.colorScheme.onSurfaceVariant)
        visible.forEachIndexed { index, person ->
            key(person.id) {
                if (index > 0) HorizontalDivider(color = Color.White.copy(alpha = .07f))
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { selectedPerson = person.id; onRouteChanged() }.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(names[person.id].orEmpty().take(1).uppercase(), color = MaterialTheme.colorScheme.primary)
                        }
                        Text(names[person.id].orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (person.receivable > 0) Text(ledgerMoney(person.receivable), style = MaterialTheme.typography.bodyLarge)
                        else Text(if (person.clear) "Sem pendências" else if (person.payable > 0) "Eu devo" else "A vencer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(AssetChevronIcon, null, Modifier.size(16.dp))
                    }
                    if (person.overdue > 0 || person.nextDue != null) Row(Modifier.fillMaxWidth().padding(start = 42.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (person.overdue > 0) Text("Vencido · ${ledgerMoney(person.overdue)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = PeopleOverdueColor)
                        else Spacer(Modifier.weight(1f))
                        person.nextDue?.let { Text("Próximo · ${AppDates.date(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (person.payable > 0) Text("Eu devo · ${ledgerMoney(person.payable)}", Modifier.padding(start = 42.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (person.receivable == 0L && person.futureReceivable > 0) Text("A receber futuramente · ${ledgerMoney(person.futureReceivable)}", Modifier.padding(start = 42.dp), style = MaterialTheme.typography.bodySmall)
                    if (person.payable == 0L && person.futurePayable > 0) Text("Eu devo futuramente · ${ledgerMoney(person.futurePayable)}", Modifier.padding(start = 42.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    when (dialog) {
        "future" -> PeopleFutureDialog(future, state, { dialog = "" }) { changeReference(it); dialog = "" }
        "add" -> AssetDialog("Adicionar pessoa", state, { dialog = "" }, confirmLabel = "Adicionar", confirmEnabled = name.trim().length in 1..120, confirm = { model.addPerson(name) { dialog = "" } }) {
            LedgerField("Nome", name) { name = it }
            if (name.trim().length > 120) Text("Use até 120 caracteres.", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun PeopleMetric(label: String, cents: Long, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(ledgerMoney(cents), fontSize = if (compact) 16.sp else 24.sp)
    }
}

@Composable
internal fun PeopleReference(reference: YearMonth, months: List<YearMonth>, state: FinanceUiState, change: (YearMonth) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Até a fatura de", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { open = true }) { Text("${peopleMonth(reference)} ⌄", color = MaterialTheme.colorScheme.onSurface) }
    }
    if (open) AssetDialog("Referência", state, { open = false }) {
        months.forEach { month -> TextButton(onClick = { open = false; change(month) }, modifier = Modifier.fillMaxWidth()) {
            Text(peopleMonth(month), Modifier.weight(1f), color = if (reference == month) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (reference == month) Text("✓")
        } }
    }
}

@Composable
internal fun PeopleShortcut(title: String, detail: String, value: String, click: () -> Unit) {
    GlassPanel(Modifier.clickable(role = Role.Button, onClick = click)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(AssetChevronIcon, null, Modifier.size(17.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(detail, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun PeopleFutureDialog(entries: List<PersonInvoice>, state: FinanceUiState, close: () -> Unit, select: (YearMonth) -> Unit) {
    AssetDialog("Próximas parcelas", state, close, subtitle = "Saldo familiar conciliado a receber") {
        entries.groupBy { it.invoice.month }.toSortedMap().forEach { (month, values) ->
            TextButton(onClick = { select(month) }, modifier = Modifier.fillMaxWidth()) {
                Text(peopleMonth(month), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Text(ledgerMoney(values.sumOf { it.share.receivable }))
            }
            Text("Partes registradas: ${ledgerMoney(values.sumOf { it.share.cents })}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = Color.White.copy(alpha = .07f))
        }
    }
}
