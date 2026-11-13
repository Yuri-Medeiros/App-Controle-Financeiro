package br.com.yurimedeiros.meufinanceiro.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.yurimedeiros.meufinanceiro.formatting.AppDates
import br.com.yurimedeiros.meufinanceiro.ledger.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun PersonFinances(
    personId: Long, state: FinanceUiState, model: FinanceViewModel,
    overview: PeopleOverview, months: List<YearMonth>, changeReference: (YearMonth) -> Unit, close: () -> Unit,
) {
    val person = overview.people.firstOrNull { it.id == personId }
    if (person == null) { Text("Pessoa não encontrada"); TextButton(onClick = close) { Text("Voltar") }; return }
    val name = personLabel(state, personId)
    var action by remember { mutableStateOf("") }
    var postpone by remember { mutableStateOf<DebtBalance?>(null) }
    var newDue by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var detailedMonth by remember(overview.reference) { mutableStateOf<YearMonth?>(null) }
    var limit by remember(overview.reference) { mutableIntStateOf(12) }
    var historyLimit by remember { mutableIntStateOf(30) }
    val expanded = remember(overview.reference) { mutableStateMapOf<YearMonth, Boolean>() }
    val context = LocalContext.current
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = close) { Icon(AssetChevronIcon, "Voltar para Pessoas", Modifier.graphicsLayer { rotationZ = 180f }) }
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        key(action) { AssetInfoButton("Informações de $name") {
            Text("A receber mostra os saldos atuais até a referência escolhida. Vencidos fazem parte desse total.")
            Text("Fora do cartão: vencimentos até ${AppDates.date(overview.outsideCutoff)}.")
            Text("Pagar ao banco não quita a dívida da pessoa. O total individual do banco depende dos rateios confirmados.")
            Text("Recebimentos históricos não são presumidos. Eu devo fica separado de A receber.")
            TextButton(onClick = { action = "debt" }) { Text("Nova dívida") }
        } }
    }
    PeopleReference(overview.reference, months, state, changeReference)
    Text("A receber de $name", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(ledgerMoney(person.receivable), fontSize = 36.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Vencido", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ledgerMoney(person.overdue), style = MaterialTheme.typography.titleMedium, color = if (person.overdue > 0) PeopleOverdueColor else MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Eu devo a $name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ledgerMoney(person.payable), style = MaterialTheme.typography.titleMedium)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { action = "receive" }, enabled = !state.syncing && (person.receivable > 0 || person.futureReceivable > 0), modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = FinanceInputShape, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Receber") }
        OutlinedButton(onClick = { action = "pay" }, enabled = !state.syncing && (person.payable > 0 || person.futurePayable > 0), modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = FinanceInputShape, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Pagar") }
        OutlinedButton(onClick = { action = "statement" }, modifier = Modifier.weight(1.4f).heightIn(min = 48.dp), shape = FinanceInputShape, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Demonstrativo", style = MaterialTheme.typography.labelMedium) }
    }
    val current = person.invoices.filter { it.invoice.month <= overview.reference }
    GlassPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Por fatura", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${current.count { it.share.receivable > 0 }} em aberto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (current.isEmpty()) Text("Nenhuma participação registrada até esta referência.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        current.take(limit).forEachIndexed { index, entry ->
            val invoice = entry.invoice
            val share = entry.share
            val open = expanded[invoice.month] == true
            if (index > 0) HorizontalDivider(color = Color.White.copy(alpha = .07f))
            Column(Modifier.fillMaxWidth().semantics { stateDescription = if (open) "Expandido" else "Recolhido" }
                .clickable(role = Role.Button) { expanded[invoice.month] = !open }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(peopleMonth(invoice.month), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(ledgerMoney(share.receivable), style = MaterialTheme.typography.bodyLarge)
                    Icon(AssetChevronIcon, null, Modifier.size(17.dp).graphicsLayer { rotationZ = if (open) 90f else 0f })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val late = share.receivable > 0 && invoice.due?.let { it < today } == true
                    Text(if (share.receivable == 0L) "Sem saldo familiar aberto" else invoice.due?.let { "${if (late) "Vencido em" else "Vence em"} ${AppDates.date(it)}" } ?: "Vencimento a confirmar", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (late) PeopleOverdueColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (invoice.remaining == 0L && invoice.imported && invoice.warnings.isEmpty()) Text("Banco quitado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (open) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PeopleMetric("Parte na fatura", share.cents, Modifier.weight(1f), compact = true)
                    PeopleMetric("Recebido", share.received, Modifier.weight(1f), compact = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PeopleMetric("Pago ao banco", share.bankPaid, Modifier.weight(1f), compact = true)
                    PeopleMetric("Falta receber", share.receivable, Modifier.weight(1f), compact = true)
                }
                if (invoice.unallocated > 0) Text("Há pagamentos ao banco ainda sem divisão por pessoa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                invoice.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                share.items.forEach { item ->
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(ledgerMoney(item.cents), style = MaterialTheme.typography.bodyMedium)
                        }
                        val detail = listOfNotNull(item.date?.let(AppDates::date), item.installment?.let { "Parcela $it" })
                        if (detail.isNotEmpty()) Text(detail.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (current.size > limit) TextButton(onClick = { limit += 12 }) { Text("Ver mais faturas") }
    }
    val future = person.invoices.filter { it.invoice.month > overview.reference }
    if (future.isNotEmpty()) PeopleShortcut("Próximas parcelas", "Saldo a receber nas próximas faturas", ledgerMoney(person.futureInstallments)) { action = "future" }
    if (person.outside.isNotEmpty()) {
        val openOutside = person.outside.filter { it.remaining > 0 }
        val totalReceive = openOutside.filter { it.direction == DebtDirection.RECEIVE }.sumOf { it.remaining }
        val totalPay = openOutside.filter { it.direction == DebtDirection.PAY }.sumOf { it.remaining }
        PeopleShortcut("Fora do cartão", "A receber ${ledgerMoney(totalReceive)} · Eu devo ${ledgerMoney(totalPay)}", "") { action = "outside" }
    }
    OutlinedButton(onClick = { historyLimit = 30; action = "history" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = FinanceInputShape) { Text("Histórico") }
    state.error?.takeIf { action.isBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    when (action) {
        "receive" -> PersonTransferDialog(personId, state, model, { action = "" })
        "pay" -> PersonTransferDialog(personId, state, model, { action = "" }, DebtDirection.PAY)
        "debt" -> ManualDebtDialog(personId, state, model) { action = "" }
        "future" -> PeopleFutureDialog(future, state, { action = "" }) { changeReference(it); action = "" }
        "outside" -> AssetDialog("Fora do cartão", state, { action = "" }) {
            person.outside.forEach { debt ->
                Text(debt.title, style = MaterialTheme.typography.titleSmall)
                Text("${if (debt.direction == DebtDirection.RECEIVE) "A receber" else "Eu devo"} · ${ledgerMoney(debt.remaining)}")
                Text("Vencimento · ${AppDates.date(debt.due)}", style = MaterialTheme.typography.bodySmall)
                if (debt.paidCents > 0) Text("Pagamentos · ${ledgerMoney(debt.paidCents)}", style = MaterialTheme.typography.bodySmall)
                if (debt.creditedCents > 0) Text("Créditos · ${ledgerMoney(debt.creditedCents)}", style = MaterialTheme.typography.bodySmall)
                if (debt.remaining > 0) TextButton(onClick = { postpone = debt; newDue = AppDates.date(debt.due); reason = ""; localError = null; action = "postpone" }) { Text("Alterar vencimento") }
                HorizontalDivider()
            }
        }
        "postpone" -> postpone?.let { debt -> AssetDialog("Alterar vencimento", state, { action = "outside" }, confirm = {
            try { model.saveLedger(LedgerAction.Postpone(debt.id, AppDates.parse(newDue), reason.trim())) { action = "outside" } }
            catch (e: Exception) { localError = e.message ?: "Revise a data e o motivo." }
        }) {
            Text(debt.title)
            LedgerField("Nova data", newDue) { newDue = it }
            LedgerField("Motivo", reason) { reason = it }
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }
        "history" -> {
            val history = remember(personId, state.ledger, state.movements) { PeopleInsights.history(personId, state.ledger, state.movements) }
            AssetDialog("Histórico", state, { action = "" }, subtitle = name) {
                if (history.isEmpty()) Text("Nenhum recebimento, pagamento ou ajuste registrado.")
                history.take(historyLimit).forEach { entry ->
                    entry.date?.let { Text(AppDates.date(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(entry.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        entry.cents?.let { Text(ledgerMoney(it)) }
                    }
                    if (entry.detail.isNotBlank()) Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                }
                if (history.size > historyLimit) TextButton(onClick = { historyLimit += 30 }) { Text("Ver mais") }
            }
        }
        "statement" -> {
            val selectedMonth = detailedMonth?.takeIf { month -> person.invoices.any { it.invoice.month == month } }
            val text = PeopleInsights.statement(name, overview, person, selectedMonth)
            AssetDialog("Demonstrativo", state, { action = "" }, confirmLabel = "Compartilhar", confirm = {
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                context.startActivity(Intent.createChooser(intent, "Compartilhar demonstrativo"))
                action = ""
            }) {
                LedgerChoice("Conteúdo", selectedMonth?.let(::peopleMonth) ?: "Resumo", listOf("Resumo até a referência" to { detailedMonth = null }) + person.invoices.map { entry -> peopleMonth(entry.invoice.month) to { detailedMonth = entry.invoice.month } })
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
