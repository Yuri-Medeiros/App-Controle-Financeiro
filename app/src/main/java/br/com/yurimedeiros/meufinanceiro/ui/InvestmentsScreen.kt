package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.assets.AssetInsights
import br.com.yurimedeiros.meufinanceiro.assets.AssetKind
import br.com.yurimedeiros.meufinanceiro.assets.InvestmentPresentation
import br.com.yurimedeiros.meufinanceiro.assets.InvestmentType

@Composable
internal fun InvestmentsScreen(state: FinanceUiState, onBack: () -> Unit, onRefresh: () -> Unit, onAsset: (String) -> Unit) {
    val book = remember(state.ledger.assets) { AssetInsights.forKind(state.ledger.assets, AssetKind.INVESTMENT) }
    val live = book.assets.filter { !it.disposed }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack) {
            Icon(AssetChevronIcon, contentDescription = "Voltar ao patrimônio", modifier = Modifier.size(22.dp).rotate(180f))
        }
        Text("Investimentos", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        AssetRefreshButton(loading = state.syncing,
            enabled = state.unlocked && state.visible && state.statements.isEmpty() && state.custody.isEmpty(),
            onClick = onRefresh, description = "Atualizar investimentos")
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text(ledgerMoney(live.mapNotNull { it.valueCents }.sum()), style = MaterialTheme.typography.headlineLarge)
    AssetCharts(book, individualAssets = true)
    GlassPanel {
        Text("Investimentos", style = MaterialTheme.typography.titleMedium)
        if (book.assets.isEmpty()) Text("Nenhum investimento cadastrado.")
        val groups = book.assets.groupBy(InvestmentPresentation::type)
        InvestmentType.entries.forEach { type ->
            groups[type]?.let { investments ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(type.label, modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    Column {
                        investments.forEachIndexed { index, asset ->
                            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Ver detalhes de ${InvestmentPresentation.label(asset)}") {
                                onAsset(asset.id)
                            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(InvestmentPresentation.label(asset), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(asset.valueCents?.let(::ledgerMoney) ?: "—", style = MaterialTheme.typography.bodyMedium)
                                Icon(AssetChevronIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            if (index < investments.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}
