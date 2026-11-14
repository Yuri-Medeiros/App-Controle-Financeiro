package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.formatting.AppDates
import br.com.yurimedeiros.meufinanceiro.importing.CounterpartySuggestion
import br.com.yurimedeiros.meufinanceiro.importing.ReviewMovement
import kotlin.math.abs

internal fun bankMovementParty(movement: ReviewMovement): String {
    movement.friendlyName.trim().takeIf { it.isNotEmpty() }?.let { return it }
    val recognized = CounterpartySuggestion.identify(movement.row).displayName.trim()
    if (recognized.isNotEmpty() && recognized != movement.row.description.trim()) return recognized
    return if (movement.row.cents > 0) "Origem não identificada" else "Destino não identificado"
}

@Composable
private fun BankMovementSummary(movement: ReviewMovement) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(bankMovementParty(movement), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ledgerMoney(abs(movement.row.cents)), style = MaterialTheme.typography.bodyMedium)
            Text(AppDates.date(movement.row.date), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun BankMovementChoice(
    title: String, selected: ReviewMovement?, options: List<ReviewMovement>, enabled: Boolean,
    onSelect: (ReviewMovement) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val sorted = remember(options) { options.sortedWith(compareByDescending<ReviewMovement> { it.row.date }.thenByDescending { it.id }) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Extrato", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { open = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = FinanceInputShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                if (selected == null) Text("Escolher", style = MaterialTheme.typography.bodyMedium)
                else BankMovementSummary(selected)
            }
            Spacer(Modifier.width(8.dp))
            Text("⌄", color = MaterialTheme.colorScheme.primary)
        }
    }
    if (open && LocalFinanceInteractive.current) AppDialog(onDismissRequest = { open = false }) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF0C162B),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (sorted.isEmpty()) Text("Nenhuma movimentação disponível.")
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    itemsIndexed(sorted, key = { _, movement -> movement.id }) { index, movement ->
                        Surface(shape = FinanceInputShape, color = if (selected?.id == movement.id)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent) {
                            Column(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button,
                                onClickLabel = "Selecionar ${bankMovementParty(movement)}") {
                                onSelect(movement); open = false
                            }.padding(horizontal = 12.dp, vertical = 16.dp)) {
                                BankMovementSummary(movement)
                            }
                        }
                        if (index < sorted.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
                TextButton(onClick = { open = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Fechar") }
            }
        }
    }
}
