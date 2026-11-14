package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import br.com.yurimedeiros.meufinanceiro.formatting.AppDates
import br.com.yurimedeiros.meufinanceiro.importing.CustodyPosition
import br.com.yurimedeiros.meufinanceiro.importing.CustodyStatement

/** A document review needs room for the holdings, rather than a nested scrolling alert. */
@Composable
internal fun CustodyReviewScreen(
    statement: CustodyStatement, state: FinanceUiState,
    onCancel: () -> Unit, onConfirm: () -> Unit,
) {
    // Keep expansion and scroll state outside the lock gate, just like the pending document.
    var expanded by remember(statement) { mutableStateOf(emptySet<String>()) }
    val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
    if (LocalFinanceInteractive.current) AppDialog(
        onDismissRequest = { if (!state.syncing) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF070D20)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CUSTÓDIA NUBANK", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Revisar investimentos", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("Posição em ${AppDates.date(statement.date)}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll,
                    contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        GlassPanel(highlighted = true) {
                            Text("Total líquido estimado", style = MaterialTheme.typography.labelLarge)
                            Text(ledgerMoney(statement.net), style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            ReviewValue("Total bruto", ledgerMoney(statement.gross))
                            Text("Este é o valor que será usado no patrimônio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Text("${statement.positions.size} investimentos", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    items(statement.positions, key = { it.name }) { position ->
                        CustodyPositionCard(position, position.name in expanded) {
                            expanded = if (position.name in expanded) expanded - position.name else expanded + position.name
                        }
                    }
                    item {
                        Text("A atualização não cria movimentações bancárias. Confira os valores antes de confirmar.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                }
                Surface(color = Color(0xFF0C162B), shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = onConfirm, enabled = !state.syncing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = FinanceInputShape) {
                            if (state.syncing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.syncing) "Atualizando…" else "Confirmar atualização")
                        }
                        TextButton(onClick = onCancel, enabled = !state.syncing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancelar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustodyPositionCard(position: CustodyPosition, expanded: Boolean, onToggle: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF0C162B),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(position.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Líquido estimado", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ledgerMoney(position.net), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            ReviewValue("Valor bruto", ledgerMoney(position.gross))
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                position.description.split('·').map(String::trim).filter(String::isNotEmpty).forEach { detail ->
                    val prefix = listOf("Vencimento", "quantidade", "aplicado", "IR", "IOF", "B3")
                        .firstOrNull { detail.startsWith("$it ", ignoreCase = true) }
                    if (prefix == null) Text(detail, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else ReviewValue(when (prefix) {
                        "quantidade" -> "Quantidade"; "aplicado" -> "Valor aplicado"; else -> prefix
                    }, detail.substring(prefix.length).trim())
                }
            }
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (expanded) "Ocultar detalhes" else "Ver detalhes do investimento")
            }
        }
    }
}

@Composable
private fun ReviewValue(label: String, value: String) {
    // Stack labels above values so large fonts and long monetary amounts never collide.
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
