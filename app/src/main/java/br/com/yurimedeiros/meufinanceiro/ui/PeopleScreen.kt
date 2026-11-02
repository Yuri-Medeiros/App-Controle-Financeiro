package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val AddPersonIcon = ImageVector.Builder(
    "Adicionar pessoa", 24.dp, 24.dp, 24f, 24f,
).addPath(
    PathParser().parsePathString("M11,4H13V11H20V13H13V20H11V13H4V11H11Z").toNodes(),
    fill = SolidColor(Color.Black),
).build()

@Composable
internal fun PeopleScreen(state: FinanceUiState, model: FinanceViewModel) {
    var selectedPerson by remember { mutableStateOf<Long?>(null) }
    selectedPerson?.let { id -> PersonFinances(id, state, model) { selectedPerson = null } }
    var search by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            search, { search = it }, modifier = Modifier.weight(1f),
            placeholder = { Text("Buscar pessoa") },
            shape = FinanceInputShape, singleLine = true,
        )
        IconButton(
            onClick = { name = ""; adding = true },
            enabled = !state.syncing,
            modifier = Modifier.size(56.dp).clip(FinanceInputShape)
                .glassSurface().border(BorderStroke(1.dp, GlassBorder), FinanceInputShape),
        ) {
            Icon(AddPersonIcon, contentDescription = "Adicionar pessoa",
                tint = NeonPalette[0], modifier = Modifier.size(24.dp))
        }
    }
    state.error?.takeIf { !adding }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val people = state.people.filter { it.name.contains(search.trim(), ignoreCase = true) }
    if (people.isEmpty()) {
        Text(
            if (search.isBlank()) "Nenhuma pessoa cadastrada" else "Nenhuma pessoa encontrada",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    people.forEach { person ->
        key(person.id) {
            GlassPanel(modifier = Modifier.clickable { selectedPerson = person.id }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).background(NeonPalette[0].copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(person.name.take(1).uppercase(), color = NeonPalette[0],
                            style = MaterialTheme.typography.labelLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(person.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val debts = state.ledger.debts.filter { it.personId == person.id }
                        val receivable = debts.filter { it.direction == br.com.yurimedeiros.meufinanceiro.ledger.DebtDirection.RECEIVE }.sumOf { it.remaining }
                        val payable = debts.filter { it.direction == br.com.yurimedeiros.meufinanceiro.ledger.DebtDirection.PAY }.sumOf { it.remaining }
                        if (receivable > 0 || payable > 0) Text("Receber ${ledgerMoney(receivable)} · pagar ${ledgerMoney(payable)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { if (!state.syncing) adding = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF080F24),
            tonalElevation = 0.dp,
            modifier = Modifier.border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(22.dp)),
            title = { Text("Adicionar pessoa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        name, { name = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome") }, shape = FinanceInputShape, singleLine = true,
                        enabled = !state.syncing, isError = name.trim().length > 120,
                        supportingText = { Text("Até 120 caracteres") },
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                GlassButton(
                    onClick = { model.addPerson(name) { adding = false } },
                    enabled = !state.syncing && name.isNotBlank() && name.trim().length <= 120,
                ) { Text("Adicionar") }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }, enabled = !state.syncing) { Text("Cancelar") }
            },
        )
    }
}
