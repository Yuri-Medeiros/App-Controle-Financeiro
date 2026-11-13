package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.yurimedeiros.meufinanceiro.formatting.AppDates
import br.com.yurimedeiros.meufinanceiro.importing.*
import br.com.yurimedeiros.meufinanceiro.ledger.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun invoiceMonth(month: YearMonth) = month.format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.forLanguageTag("pt-BR"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) }
private fun invoicePerson(state: FinanceUiState, id: Long) = when (id) { 0L -> "Você"; InvoiceBook.UNASSIGNED -> "A revisar"; else -> personLabel(state, id) }
private fun invoiceEdit(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')

@Composable
internal fun InvoicesScreen(state: FinanceUiState, model: FinanceViewModel, onMovements: () -> Unit, onMonthChanged: () -> Unit) {
    val invoices = remember(state.ledger, state.movements) { InvoiceBook.invoices(state.ledger, state.movements) }
    var selected by remember { mutableStateOf<YearMonth?>(null) }
    val invoice = invoices.firstOrNull { it.month == selected } ?: invoices.firstOrNull { it.month == InvoiceBook.defaultMonth(invoices, LocalDate.now()) }
    var dialog by remember { mutableStateOf("") }
    var receiptPerson by remember { mutableStateOf<Long?>(null) }
    val expanded = remember(invoice?.month) { mutableStateMapOf<Long, Boolean>() }
    fun select(month: YearMonth) { selected = month; dialog = ""; onMonthChanged() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Faturas", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        key(dialog) { AssetInfoButton("Informações da fatura") {
            Text("Cartão Nubank")
            Text("Receber de uma pessoa e pagar ao banco são controles separados.")
            Text("Parcelas futuras incluem somente compromissos registrados.")
            if (invoice != null) TextButton(onClick = { dialog = "due" }) { Text("Alterar vencimento") }
        } }
    }
    if (invoice == null) {
        Spacer(Modifier.height(32.dp))
        Text("Nenhuma fatura registrada", style = MaterialTheme.typography.titleLarge)
        Text("Importe sua fatura em Movimentações.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onMovements) { Text("Abrir Movimentações") }
        return
    }
    val index = invoices.indexOf(invoice)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { select(invoices[index - 1].month) }, enabled = index > 0) {
            Icon(AssetChevronIcon, "Fatura anterior", Modifier.graphicsLayer { rotationZ = 180f })
        }
        Column(Modifier.weight(1f).clickable(role = Role.Button) { dialog = "months" }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${invoiceMonth(invoice.month)} ⌄", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(invoice.due?.let { "Vencimento em ${AppDates.date(it)}" } ?: "Vencimento a confirmar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { select(invoices[index + 1].month) }, enabled = index < invoices.lastIndex) { Icon(AssetChevronIcon, "Próxima fatura") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total da fatura", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(when { !invoice.imported -> "Prevista"; invoice.warnings.isNotEmpty() -> "A revisar"; invoice.remaining == 0L -> "Quitada"; else -> "Em aberto" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        Text(ledgerMoney(invoice.total), fontSize = 36.sp, fontWeight = FontWeight.Normal)
        LinearProgressIndicator(progress = { if (invoice.total > 0) (invoice.paid.toDouble() / invoice.total).toFloat().coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InvoiceMetric("Pago ao banco", invoice.paid)
            InvoiceMetric("Falta pagar", invoice.remaining, trailing = true)
        }
        if (invoice.paid > invoice.total) Text("Crédito na fatura: ${ledgerMoney(invoice.paid - invoice.total)}", style = MaterialTheme.typography.bodySmall)
        if (invoice.imported) Button(onClick = { dialog = "payment" }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = FinanceInputShape) { Text("Registrar pagamento") }
        if (invoice.warnings.isNotEmpty()) {
            invoice.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onMovements) { Text("Revisar em Movimentações") }
        }
    }
    GlassPanel(contentPadding = PaddingValues(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Por pessoa", style = MaterialTheme.typography.titleMedium)
            val count = invoice.people.count { it.id >= 0 }
            Text("$count ${if (count == 1) "pessoa" else "pessoas"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        invoice.people.forEachIndexed { i, person ->
            if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = .07f))
            val open = expanded[person.id] == true
            Column(Modifier.fillMaxWidth().semantics { stateDescription = if (open) "Expandido" else "Recolhido" }
                .clickable(role = Role.Button) { expanded[person.id] = !open }.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (person.id < 0) Color(0xFFF5BA79) else NeonPalette[i % NeonPalette.size], CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(invoicePerson(state, person.id), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(ledgerMoney(person.cents), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    Icon(AssetChevronIcon, null, Modifier.size(17.dp).graphicsLayer { rotationZ = if (open) 90f else 0f })
                }
                Row(Modifier.fillMaxWidth().padding(start = 17.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (person.id > 0) "A receber · ${ledgerMoney(person.receivable)}" else if (person.id == 0L) "Pago ao banco" else "Defina os responsáveis", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (person.id >= 0) Text(if (person.id == 0L) ledgerMoney(person.bankPaid) else "Banco · ${ledgerMoney(person.bankPaid)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (open) {
                if (person.id > 0) Row(Modifier.fillMaxWidth().padding(start = 17.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    InvoiceMetric("Recebido", person.received)
                    InvoiceMetric("Pago ao banco", person.bankPaid, trailing = true)
                }
                person.items.forEach { item ->
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    Column(Modifier.fillMaxWidth().padding(start = 17.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(ledgerMoney(item.cents), style = MaterialTheme.typography.bodyMedium)
                        }
                        val details = listOfNotNull(item.date?.let(AppDates::date), item.installment?.let { "Parcela $it" })
                        if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (person.id > 0 && person.receivable > 0) TextButton(onClick = { receiptPerson = person.id }, enabled = !state.syncing) { Text("Registrar recebimento") }
                if (person.id < 0) TextButton(onClick = onMovements) { Text("Revisar em Movimentações") }
            }
        }
        if (invoice.unallocated > 0) TextButton(onClick = { dialog = "payment" }) { Text("Dividir pagamentos · ${ledgerMoney(invoice.unallocated)}") }
    }
    val previous = InvoiceBook.previousDebts(invoice, invoices, state.ledger, state.movements)
    if (previous.isNotEmpty()) InvoiceShortcut("Pendências anteriores", "${previous.map { it.personId }.distinct().size} pessoas · Faturas quitadas", ledgerMoney(previous.sumOf { it.remaining })) { dialog = "debts" }
    val future = invoices.filter { it.month > invoice.month }
    if (future.isNotEmpty()) InvoiceShortcut("Próximas faturas", "${invoiceMonth(future.first().month)} · ${ledgerMoney(future.first().total)}", "Ver todas") { dialog = "future" }
    when (dialog) {
        "months" -> AssetDialog("Selecionar fatura", state, { dialog = "" }) {
            invoices.groupBy { it.month.year }.forEach { (year, list) ->
                Text(year.toString(), style = MaterialTheme.typography.titleSmall)
                list.chunked(3).forEach { chunk -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chunk.forEach { f -> OutlinedButton(onClick = { select(f.month) }, modifier = Modifier.weight(1f), shape = FinanceInputShape) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(f.month.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("pt-BR"))), color = if (f.month == invoice.month) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(if (!f.imported) "Prevista" else if (f.remaining == 0L && f.warnings.isEmpty()) "Quitada" else "Aberta", style = MaterialTheme.typography.labelSmall)
                        }
                    } }
                    repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                } }
            }
        }
        "future" -> AssetDialog("Próximas faturas", state, { dialog = "" }, subtitle = "Somente compromissos registrados") {
            val maximum = future.maxOf { it.total }.coerceAtLeast(1)
            future.forEach { f -> Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { select(f.month) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(invoiceMonth(f.month)); Text(ledgerMoney(f.total)) }
                LinearProgressIndicator(progress = { (f.total.toDouble() / maximum).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } }
        }
        "debts" -> AssetDialog("Pendências anteriores", state, { dialog = "" }, subtitle = "A receber · Faturas quitadas no banco") {
            previous.groupBy { it.personId }.forEach { (id, debts) ->
                Text(invoicePerson(state, id), style = MaterialTheme.typography.titleMedium)
                debts.groupBy { InvoiceBook.debtMonth(it, state.ledger, state.movements) }.forEach { (month, list) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(month?.let(::invoiceMonth).orEmpty()); Text(ledgerMoney(list.sumOf { it.remaining })) }
                }
                TextButton(onClick = { receiptPerson = id; dialog = "" }) { Text("Registrar recebimento") }
                HorizontalDivider()
            }
        }
        "payment" -> InvoicePaymentDialog(invoice, state, model) { dialog = "" }
        "due" -> InvoiceDueDialog(invoice, state, model) { dialog = "" }
    }
    receiptPerson?.let { id -> PersonTransferDialog(id, state, model, close = { receiptPerson = null }) }
}

@Composable
private fun InvoiceMetric(label: String, cents: Long, trailing: Boolean = false) {
    Column(horizontalAlignment = if (trailing) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(ledgerMoney(cents), style = MaterialTheme.typography.titleMedium, color = if (trailing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InvoiceShortcut(title: String, subtitle: String, value: String, click: () -> Unit) {
    GlassPanel(Modifier.clickable(role = Role.Button, onClick = click)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(AssetChevronIcon, null, Modifier.size(18.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(subtitle, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InvoiceDueDialog(invoice: Invoice, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var due by remember { mutableStateOf(invoice.due?.let(AppDates::date) ?: AppDates.date(invoice.month.atDay(8))) }
    var error by remember { mutableStateOf<String?>(null) }
    AssetDialog("Vencimento", state, close, confirm = {
        try {
            val date = AppDates.parse(due)
            require(YearMonth.from(date) == invoice.month) { "Informe uma data deste mês." }
            model.saveLedger(LedgerAction.Planning(state.ledger.planning.copy(invoiceDates = state.ledger.planning.invoiceDates + (invoice.month.toString() to date))), close)
        } catch (e: Exception) { error = e.message }
    }) {
        LedgerField("Data", due) { due = it }
        Text("Confirme a data ajustada para dia útil.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun InvoicePaymentDialog(invoice: Invoice, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var selected by remember { mutableLongStateOf(0) }
    var reconcile by remember { mutableStateOf<ReviewMovement?>(null) }
    val divided = state.ledger.actions.filterIsInstance<LedgerAction.InvoiceAllocation>()
    val eligible = invoice.payments.filter { p -> divided.none { it.cardMovementId == p.id } }
    val source = eligible.firstOrNull { it.id == selected }
    val amounts = remember { mutableStateMapOf<Long, String>() }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selected) {
        amounts.clear()
        source?.let { InvoiceBook.suggestBankAllocation(invoice, -it.row.cents).forEach { (id, cents) -> amounts[id] = invoiceEdit(cents) } }
    }
    if (reconcile != null) { MovementFinanceDialog(reconcile!!, state, model) { reconcile = null }; return }
    AssetDialog("Pagamento ao banco", state, close, confirmLabel = "Confirmar divisão", confirmEnabled = source != null, confirm = {
        try {
            val row = requireNotNull(source) { "Selecione o pagamento." }
            val allocation = amounts.mapValues { FinanceLedger.money(it.value.ifBlank { "0" }) }.filterValues { it > 0 }
            val action = LedgerAction.InvoiceAllocation(requireNotNull(row.row.statement), row.pairedMovementId, row.id, allocation)
            InvoiceBook.validateAllocation(action, state.ledger, state.movements)
            model.saveLedger(action, close)
        } catch (e: Exception) { error = e.message ?: "Revise a divisão." }
    }) {
        LedgerChoice("Pagamento no extrato", source?.let { "${AppDates.date(it.row.date)} · ${ledgerMoney(-it.row.cents)}" } ?: "Escolher", eligible.map { p -> "${AppDates.date(p.row.date)} · ${ledgerMoney(-p.row.cents)}" to { selected = p.id } })
        if (eligible.isEmpty()) Text("Nenhum pagamento conciliado aguardando divisão.", style = MaterialTheme.typography.bodyMedium)
        if (source != null) {
            Text("Divisão sugerida · Ajuste se necessário", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            invoice.people.filter { it.id >= 0 && it.bankCapacity > 0 }.forEach { p ->
                LedgerField(invoicePerson(state, p.id), amounts[p.id].orEmpty(), monetary = true) { amounts[p.id] = it }
            }
        }
        val pending = state.movements.filter { it.row.kind == DocumentKind.NUBANK_CARD && it.row.statement?.let(YearMonth::from) == invoice.month && PaymentMatching.isPayment(it.row) && it.status == "PAYMENT_PENDING" }
        pending.forEach { p -> OutlinedButton(onClick = { reconcile = p }, enabled = !state.syncing) { Text("Conciliar ${AppDates.date(p.row.date)} · ${ledgerMoney(-p.row.cents)}") } }
        if (invoice.payments.isEmpty() && pending.isEmpty()) Text("Importe o extrato e a fatura para registrar um pagamento realizado.", style = MaterialTheme.typography.bodySmall)
        divided.filter { a -> invoice.payments.any { it.id == a.cardMovementId } }.forEach { a ->
            HorizontalDivider()
            Text("${AppDates.date(invoice.payments.single { it.id == a.cardMovementId }.row.date)} · ${ledgerMoney(a.allocations.values.sum())}", style = MaterialTheme.typography.titleSmall)
            a.allocations.forEach { (id, cents) -> Text("${invoicePerson(state, id)} · ${ledgerMoney(cents)}", style = MaterialTheme.typography.bodySmall) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun PersonTransferDialog(person: Long, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit, direction: DebtDirection = DebtDirection.RECEIVE) {
    val debts = state.ledger.debts.filter { it.personId == person && it.direction == direction && it.remaining > 0 }.sortedWith(compareBy<DebtBalance> { it.due }.thenBy { it.id })
    var selected by remember { mutableLongStateOf(0) }
    val sources = state.movements.filter { it.row.kind == DocumentKind.NUBANK_ACCOUNT && (if (direction == DebtDirection.RECEIVE) it.row.cents > 0 else it.row.cents < 0) && it.status !in setOf("CONFLICT", "IGNORED") && it.id !in state.ledger.usedMovementIds && !PaymentMatching.isPayment(it.row) }
    val source = sources.firstOrNull { it.id == selected }
    val amounts = remember { mutableStateMapOf<String, String>() }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selected, debts) {
        amounts.clear()
        source?.let { InvoiceBook.suggestReceipt(debts, kotlin.math.abs(it.row.cents)).forEach { (id, cents) -> amounts[id] = invoiceEdit(cents) } }
    }
    AssetDialog(if (direction == DebtDirection.RECEIVE) "Recebimento" else "Pagamento", state, close, subtitle = invoicePerson(state, person), confirmLabel = if (direction == DebtDirection.RECEIVE) "Confirmar recebimento" else "Confirmar pagamento", confirmEnabled = source != null, confirm = {
        try {
            val row = requireNotNull(source)
            val allocations = amounts.mapValues { FinanceLedger.money(it.value.ifBlank { "0" }) }.filterValues { it > 0 }
            require(allocations.values.fold(0L, Math::addExact) == kotlin.math.abs(row.row.cents)) { "Distribua exatamente ${ledgerMoney(kotlin.math.abs(row.row.cents))}." }
            model.saveLedger(LedgerAction.Payment(person, direction, row.id, row.row.date, allocations), close)
        } catch (e: Exception) { error = e.message ?: "Revise os valores." }
    }) {
        BankMovementChoice(if (direction == DebtDirection.RECEIVE) "Recebimento no extrato" else "Pagamento no extrato", source, sources, !state.syncing) { selected = it.id }
        if (source != null) {
            Text("Sugestão · Dívidas mais antigas primeiro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            debts.forEach { d ->
                Text("${d.title} · ${AppDates.date(d.due)}", style = MaterialTheme.typography.bodySmall)
                LedgerField("Em aberto · ${ledgerMoney(d.remaining)}", amounts[d.id].orEmpty(), monetary = true) { amounts[d.id] = it }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
