package br.com.yurimedeiros.meufinanceiro.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.ledger.*
import java.time.LocalDate

@Composable
internal fun PersonFinances(personId: Long, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var limit by remember { mutableIntStateOf(30) }
    var action by remember { mutableStateOf("") }
    var postpone by remember { mutableStateOf<DebtBalance?>(null) }
    var newDue by remember { mutableStateOf("") }; var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val debts = state.ledger.debts.filter { it.personId == personId }
    val today = LocalDate.now()
    val received = debts.filter { it.direction == DebtDirection.RECEIVE }
    val owed = debts.filter { it.direction == DebtDirection.PAY }
    var reference by remember { mutableStateOf("") }
    val dates = debts.filter { it.purchaseId != null }.map { it.due }.distinct().sorted()
    val shareDebts = if (reference.isBlank()) debts.filter { it.remaining > 0 && it.due <= today.withDayOfMonth(today.lengthOfMonth()) } else debts.filter { it.due.toString() == reference }
    val shareText = buildString {
        appendLine("Meu Financeiro · ${personLabel(state, personId)}")
        appendLine(if (reference.isBlank()) "Resumo atual e vencido" else "Demonstrativo · $reference")
        if (reference.isNotBlank()) shareDebts.forEach { appendLine("${it.title}: ${ledgerMoney(it.remaining)} · ${if (it.direction == DebtDirection.RECEIVE) "a receber" else "a pagar"}") }
        appendLine("A receber: ${ledgerMoney(shareDebts.filter { it.direction == DebtDirection.RECEIVE }.sumOf { it.remaining })}")
        appendLine("A pagar: ${ledgerMoney(shareDebts.filter { it.direction == DebtDirection.PAY }.sumOf { it.remaining })}")
    }
    when (action) {
        "debt" -> ManualDebtDialog(personId, state, model) { action = "" }
        "receive" -> PaymentDialog(personId, DebtDirection.RECEIVE, state, model, { action = "" })
        "pay" -> PaymentDialog(personId, DebtDirection.PAY, state, model, { action = "" })
        else -> LedgerDialog(personLabel(state, personId), state, close) {
            Text("A receber · ${ledgerMoney(received.sumOf { it.remaining })}", style = MaterialTheme.typography.titleMedium)
            Text("Eu devo · ${ledgerMoney(owed.sumOf { it.remaining })}", style = MaterialTheme.typography.titleMedium)
            val overdue = received.filter { it.due < today }.sumOf { it.remaining }
            if (overdue > 0) Text("Vencidos a receber · ${ledgerMoney(overdue)}", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { action = "debt" }) { Text("Nova dívida") }
                TextButton(onClick = { action = "receive" }) { Text("Receber") }
                TextButton(onClick = { action = "pay" }) { Text("Pagar") }
            }
            PillTabs(listOf("Em aberto", "Histórico"), tab) { tab = it; limit = 30 }
            val shown = if (tab == 0) debts.filter { it.remaining > 0 } else debts
            if (shown.isEmpty()) Text(if (tab == 0) "Sem dívidas em aberto." else "Sem histórico registrado.")
            shown.sortedBy { it.due }.take(limit).forEach { d ->
                HorizontalDivider()
                Text(d.title)
                Text("${d.due} · ${if (d.direction == DebtDirection.RECEIVE) "A receber" else "A pagar"} ${ledgerMoney(d.remaining)}")
                if (d.paidCents > 0 || d.creditedCents > 0) Text("Pagamentos ${ledgerMoney(d.paidCents)} · estornos ${ledgerMoney(d.creditedCents)}", style = MaterialTheme.typography.bodySmall)
                if (d.purchaseId == null && d.remaining > 0) TextButton(onClick = { postpone = d; newDue = d.due.toString(); reason = "" }) { Text("Alterar vencimento") }
            }
            if (shown.size > limit) TextButton(onClick = { limit += 30 }) { Text("Ver mais") }
            if (tab == 1) {
                Text("Pagamentos e alterações", style = MaterialTheme.typography.titleSmall)
                state.ledger.actions.takeLast(limit).forEach { a -> when (a) {
                    is LedgerAction.Payment -> if (a.personId == personId) Text("${a.date} · ${if (a.direction == DebtDirection.RECEIVE) "Recebido" else "Pago"} ${ledgerMoney(a.allocations.values.sum())}", style = MaterialTheme.typography.bodySmall)
                    is LedgerAction.Postpone -> if (debts.any { it.id == a.debtId }) Text("Vencimento alterado para ${a.due} · ${a.reason}", style = MaterialTheme.typography.bodySmall)
                    else -> Unit
                } }
            }
            LedgerChoice("Compartilhar", if (reference.isBlank()) "Resumo" else reference, listOf("Resumo atual e vencido" to { reference = "" }) + dates.map { date -> date.toString() to { reference = date.toString() } })
            OutlinedButton(onClick = { sharing = true }) { Text("Prévia do demonstrativo") }
        }
    }
    postpone?.let { d -> LedgerDialog("Alterar vencimento", state, { postpone = null }, confirm = {
        try { model.saveLedger(LedgerAction.Postpone(d.id, LocalDate.parse(newDue), reason.trim())) { postpone = null } }
        catch (_: Exception) { error = "Informe uma data válida e o motivo." }
    }) {
        Text("${d.title} · atual ${d.due}")
        LedgerField("Nova data AAAA-MM-DD", newDue) { newDue = it }
        LedgerField("Motivo", reason) { reason = it }
        Text("A alteração será adicionada ao histórico; pagamentos não serão apagados.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }
    if (sharing) LedgerDialog("Prévia do demonstrativo", state, { sharing = false }, confirmLabel = "Compartilhar", confirm = {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }
        context.startActivity(Intent.createChooser(intent, "Compartilhar demonstrativo")); sharing = false
    }) { Text(shareText) }
}
