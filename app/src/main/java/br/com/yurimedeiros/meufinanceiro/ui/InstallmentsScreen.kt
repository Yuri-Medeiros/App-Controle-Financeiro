package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.ledger.*
import br.com.yurimedeiros.meufinanceiro.importing.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun InstallmentsDialog(state: FinanceUiState, close: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val actions = state.ledger.actions.filterIsInstance<LedgerAction.Refund>()
    LedgerDialog("Faturas e parcelas", state, close) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("Anterior") }
            Text(month.toString())
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("Próxima") }
        }
        val imported = state.movements.filter { it.row.kind == DocumentKind.NUBANK_CARD && it.row.statement?.let(YearMonth::from) == month && it.status != "CONFLICT" }
        val bankCharges = imported.filter { !PaymentMatching.isPayment(it.row) }.sumOf { it.row.cents }
        val paid = imported.filter { it.status == "PAYMENT" }.sumOf { -it.row.cents }
        val parts = state.ledger.purchases.flatMap { p -> p.parts.filter { YearMonth.from(it.due) == month }.map { p to it } }
        val own = parts.sumOf { (p, part) ->
            val id = "${p.id}:${part.number}:0"
            (p.shares[0]?.get(part.number - 1) ?: 0) - actions.sumOf { it.credits[id] ?: 0 }
        }
        Text("Fatura importada · ${ledgerMoney(bankCharges)}", style = MaterialTheme.typography.titleMedium)
        Text("Pagamentos conciliados · ${ledgerMoney(paid)}", style = MaterialTheme.typography.bodySmall)
        Text("Minha parte confirmada · ${ledgerMoney(own)}")
        val unknown = imported.count { it.row.cents > 0 && it.id !in state.ledger.usedMovementIds }
        if (unknown > 0) Text("$unknown lançamento(s) sem responsáveis confirmados. A projeção não consolida esses valores com parcelas ainda sem vínculo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Text("Pagamento ao banco e recebimentos de familiares são independentes. Os valores previstos abaixo não são somados novamente à fatura importada.", style = MaterialTheme.typography.bodySmall)
        if (parts.isEmpty()) Text("Nenhuma parcela confirmada para este mês.")
        parts.forEach { (p, part) ->
            HorizontalDivider()
            TextButton(onClick = { expanded = if (expanded == "${p.id}:${part.number}") null else "${p.id}:${part.number}" }) {
                Text("${p.title} · ${part.number}/${p.parts.size} · ${ledgerMoney(part.cents)}")
            }
            Text("${part.due} · ${if (part.movementId == 0L) "Prevista, aguardando extrato" else "Vinculada ao extrato"}${if (part.number !in p.familyOpening && p.shares.keys.any { it > 0 }) " · histórico familiar a conciliar" else ""}", style = MaterialTheme.typography.bodySmall)
            if (expanded == "${p.id}:${part.number}") p.shares.forEach { (person, amounts) ->
                val key = "${p.id}:${part.number}:$person"
                val credits = actions.sumOf { it.credits[key] ?: 0 }
                Text("${personLabel(state, person)} · ${ledgerMoney(amounts[part.number - 1])}${if (credits > 0) " · estorno ${ledgerMoney(credits)}" else ""}")
            }
        }
    }
}
