package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.importing.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun MovementsScreen(state: FinanceUiState, model: FinanceViewModel, onRefresh: () -> Unit) {
    val selected = remember { mutableStateMapOf<Long, Pair<String, String>>() }
    LaunchedEffect(state.movements) { selected.clear() }
    Button(onClick = onRefresh, enabled = !state.syncing && state.statements.isEmpty()) { Text(if (state.syncing) "Consultando…" else "Atualizar pelo Gmail") }
    Text("Leia os documentos Nubank e Flash da sua conta Google. Nada é classificado como consumo sem sua confirmação.")
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.notice?.let { Text(it) }
    state.statements.firstOrNull()?.let { statement ->
        var reference by remember(statement.source.source) { mutableStateOf(statement.document.proposedStatement?.toString().orEmpty()) }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Confirme a referência da fatura")
                Text(statement.source.filename)
                OutlinedTextField(reference, { reference = it }, label = { Text("Referência AAAA-MM-DD") })
                Button(onClick = { model.confirmStatement(reference) }, enabled = !state.syncing) { Text("Confirmar referência") }
            }
        }
    }
    if (selected.isNotEmpty()) {
        Text("Confirmar ${selected.size} registros com estas categorias:")
        selected.forEach { (id, choice) ->
            Text("${state.movements.firstOrNull { it.id == id }?.row?.description.orEmpty()} · ${choice.first}")
        }
        Button(onClick = { model.confirmBatch(selected.toMap()) }, enabled = !state.syncing) { Text("Confirmar selecionados") }
    }
    Text("${state.movements.count { it.status == "CONFIRMED" }} confirmadas · ${state.movements.count { it.status != "CONFIRMED" }} pendentes")
    if (state.movements.isEmpty()) Text("Toque em Atualizar para buscar seus extratos. PDF de investimentos ainda não é suportado.")
    // Render a bounded page, keeping financial data out of savedInstanceState.
    var page by remember { mutableIntStateOf(0) }
    val rows = state.movements.sortedByDescending { it.row.date }
    val pages = ((rows.size + 29) / 30).coerceAtLeast(1)
    val current = page.coerceAtMost(pages - 1)
    rows.drop(current * 30).take(30).forEach { movement ->
        key(movement.id, movement.category) { MovementCard(movement, state, model, selected.containsKey(movement.id)) { choice ->
            if (choice == null) selected.remove(movement.id) else selected[movement.id] = choice
        } }
    }
    if (pages > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { page = (current - 1).coerceAtLeast(0) }, enabled = current > 0) { Text("Anterior") }
        Text("${current + 1}/$pages")
        TextButton(onClick = { page = current + 1 }, enabled = current + 1 < pages) { Text("Próxima") }
    }
}

@Composable
private fun MovementCard(movement: ReviewMovement, state: FinanceUiState, model: FinanceViewModel, selected: Boolean, onSelect: (Pair<String, String>?) -> Unit) {
    val suggestion = CounterpartySuggestion.identify(movement.row)
    val previous = state.movements.sortedBy { it.confirmedAt }.lastOrNull { it.status == "CONFIRMED" && it.origin.substringBefore(" · ") == movement.origin.substringBefore(" · ") && suggestion.learningKey != null && CounterpartySuggestion.identify(it.row).learningKey == suggestion.learningKey }
    var category by remember { mutableStateOf(movement.category.ifBlank { previous?.category.orEmpty() }) }
    var friendly by remember { mutableStateOf(movement.friendlyName) }
    var details by remember { mutableStateOf(false) }
    LaunchedEffect(category, friendly) { if (selected) onSelect(if (category.isBlank()) null else category to friendly) }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${movement.row.date} · ${movement.row.kind.name.replace('_', ' ')}")
            Text(movement.friendlyName.ifBlank { suggestion.displayName })
            Text(NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(movement.row.cents, 2)))
            Text(if (movement.status == "CONFIRMED") "Confirmada · ${movement.category}" else "Aguardando revisão")
            TextButton(onClick = { details = !details }) { Text(if (details) "Fechar detalhes" else "Revisar / editar") }
            if (details) {
                Text(movement.row.description)
                Text("Origem: ${movement.origin}")
                movement.row.statement?.let { Text("Referência da fatura: $it") }
                movement.row.balanceCents?.let { Text("Saldo no extrato: ${BigDecimal.valueOf(it, 2)}") }
                if (movement.status == "CONFLICT" || MovementRepository.needsSpecialReview(movement.row)) {
                    Text("Pendente de conciliação de pagamento, estorno ou conflito. Preservado sem classificação automática.")
                } else {
                    Text("Categorias: Moradia, Alimentação, Transporte, Saúde, Pessoal e lazer, Educação, Presentes e doações, Tarifas e impostos. Você também pode escrever uma categoria própria.")
                    OutlinedTextField(category, { category = it }, label = { Text("Categoria (confirme a sugestão)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(friendly, { friendly = it }, label = { Text("Nome amigável (opcional)") }, modifier = Modifier.fillMaxWidth())
                    if (movement.status != "CONFIRMED") Row {
                        Checkbox(checked = selected, onCheckedChange = { onSelect(if (it && category.isNotBlank()) category to friendly else null) }, enabled = category.isNotBlank() && !state.syncing)
                        Text("Incluir no lote · $category")
                    }
                    Button(onClick = { model.confirmMovement(setOf(movement.id), category, friendly) }, enabled = category.isNotBlank() && !state.syncing) { Text("Confirmar este registro") }
                }
            }
        }
    }
}
