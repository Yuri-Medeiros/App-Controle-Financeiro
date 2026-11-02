package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val IncomeColor = Color(0xFF46E4D1)
internal val ExpenseColor = Color(0xFFFF687E)
private val ChartColors = NeonPalette + Color(0xFFB790FF)

@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier.fillMaxWidth().glassSurface(highlighted).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    GlassPanel(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index, onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = NeonPalette[1].copy(alpha = 0.28f), activeContentColor = Color.White,
                    inactiveContainerColor = Color.White.copy(alpha = 0.06f), inactiveContentColor = Color(0xFFD5E3F0),
                    activeBorderColor = NeonPalette[0].copy(alpha = 0.65f), inactiveBorderColor = Color.White.copy(alpha = 0.18f),
                ),
            ) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
        }
    }
}

@Composable
internal fun OverviewScreen(state: FinanceUiState, onMovements: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    val planning = br.com.yurimedeiros.meufinanceiro.planning.PlanningRules.view(state.ledger.planning, state.ledger, state.movements, java.time.LocalDate.now())
    val confirmed = state.movements.filter { it.status == "CONFIRMED" }
    val pending = state.movements.count { it.status !in setOf("CONFIRMED", "PAYMENT", "REFUND", "FAMILY_PAYMENT", "IGNORED", "ASSET_TRANSFER", "CARD_CREDIT", "FLASH_CREDIT") }
    GlassPanel(highlighted = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Disponível até o salário", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { visible = !visible }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (visible) "Ocultar valores" else "Mostrar valores", style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(if (!visible) "••••" else planning.available?.let(::ledgerMoney) ?: "—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = if ((planning.available ?: 0) < 0) ExpenseColor else MaterialTheme.colorScheme.onSurface)
        Text(planning.warnings.firstOrNull() ?: "Antes de ${planning.nextSalary}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8EDFF))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric("Orçamento adicional", if (!visible) "••••" else if (state.ledger.planning.budgets.isEmpty()) "—" else ledgerMoney(planning.extraBudget), Modifier.weight(1f), "Não coberto por obrigações")
        val month = java.time.YearMonth.now()
        val refunds = state.ledger.actions.filterIsInstance<br.com.yurimedeiros.meufinanceiro.ledger.LedgerAction.Refund>()
        val own = state.ledger.purchases.sumOf { p -> p.parts.filter { java.time.YearMonth.from(it.due) == month }.sumOf { part ->
            (p.shares[0L]?.get(part.number - 1) ?: 0) - refunds.sumOf { it.credits["${p.id}:${part.number}:0"] ?: 0 }
        } }
        Metric("Minha parte confirmada", if (!visible) "••••" else if (state.ledger.purchases.isEmpty()) "—" else ledgerMoney(own), Modifier.weight(1f), "Vencimentos de $month")
    }
    val receivables = state.ledger.debts.filter { it.direction == br.com.yurimedeiros.meufinanceiro.ledger.DebtDirection.RECEIVE }
    if (state.ledger.debts.isNotEmpty()) GlassPanel {
        Text("Familiares", style = MaterialTheme.typography.titleSmall)
        Text(if (visible) "A receber · ${ledgerMoney(receivables.sumOf { it.remaining })}" else "A receber · ••••")
        val overdue = receivables.filter { it.due < java.time.LocalDate.now() }.sumOf { it.remaining }
        if (overdue > 0) Text(if (visible) "Vencidos · ${ledgerMoney(overdue)}" else "Vencidos · ••••", color = ExpenseColor)
        Text(if (visible) "Eu devo · ${ledgerMoney(state.ledger.debts.filter { it.direction == br.com.yurimedeiros.meufinanceiro.ledger.DebtDirection.PAY }.sumOf { it.remaining })}" else "Eu devo · ••••", style = MaterialTheme.typography.bodySmall)
    }
    val categoryCounts = confirmed.groupingBy { it.category.ifBlank { "Sem categoria" } }.eachCount().entries.sortedByDescending { it.value }
    val groups = categoryCounts.take(5).map { it.key to it.value }.toMutableList().apply {
        if (categoryCounts.size > 5) add("Outras categorias" to categoryCounts.drop(5).sumOf { it.value })
    }
    GlassPanel {
        Text("Registros por categoria", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Todos os registros confirmados", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (groups.isEmpty()) {
            EmptyChart("Sem registros", "Confirme suas movimentações para visualizar a distribuição por categoria.")
        } else {
            BoxWithConstraints {
                if (maxWidth < 310.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CategoryRing(groups, confirmed.size)
                        CategoryLegend(groups, confirmed.size)
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CategoryRing(groups, confirmed.size)
                    Box(Modifier.weight(1f)) { CategoryLegend(groups, confirmed.size) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric("Confirmadas", confirmed.size.toString(), Modifier.weight(1f))
        Metric("Para revisar", pending.toString(), Modifier.weight(1f))
    }
    OutlinedButton(onClick = onMovements, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Ver movimentações") }
}

@Composable
private fun CategoryRing(groups: List<Pair<String, Int>>, total: Int) {
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(12.dp).semantics { contentDescription = "$total registros confirmados, distribuídos por categoria" }) {
            var angle = -90f
            groups.forEachIndexed { index, group ->
                val sweep = group.second.toFloat() / total * 360f
                drawArc(ChartColors[index], angle, sweep, false, style = Stroke(width = size.minDimension * 0.17f))
                angle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(total.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Registros", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CategoryLegend(groups: List<Pair<String, Int>>, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEachIndexed { index, group ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(ChartColors[index], CircleShape))
                Text(group.first, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("${group.second * 100L / total}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyChart(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) { drawCircle(NeonPalette[1].copy(alpha = 0.28f), style = Stroke(width = 14.dp.toPx())) }
            Text("—", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PlannedScreen(section: String) {
    var tab by remember(section) { mutableIntStateOf(0) }
    when (section) {
        "Planejamento" -> {
            PillTabs(listOf("Metas", "Orçamento"), tab) { tab = it }
            GlassPanel {
                EmptyChart(if (tab == 0) "Suas próximas conquistas" else "Seu orçamento", if (tab == 0) "O cadastro de metas, valores e prazos ainda está em desenvolvimento." else "A definição de limites e o acompanhamento do orçamento ainda estão em desenvolvimento.")
            }
        }
        else -> {
            GlassPanel(highlighted = true) {
                Text("Patrimônio total", style = MaterialTheme.typography.bodyMedium)
                Text("—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Nenhum patrimônio cadastrado", style = MaterialTheme.typography.bodySmall)
            }
            PillTabs(listOf("Por tipo", "Por conta"), tab) { tab = it }
            GlassPanel {
                EmptyChart(if (tab == 0) "Distribuição por tipo" else "Distribuição por conta", "O cadastro e o acompanhamento de patrimônio ainda estão em desenvolvimento.")
            }
        }
    }
}
