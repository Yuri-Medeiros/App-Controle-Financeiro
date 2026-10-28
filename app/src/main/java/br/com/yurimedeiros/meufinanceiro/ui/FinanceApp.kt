package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

private data class Section(val title: String, val navigation: String, val description: String, val icon: ImageVector)
private fun symbol(name: String, path: String): ImageVector = ImageVector.Builder(
    name, 24.dp, 24.dp, 24f, 24f,
).addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()

private val HomeIcon = symbol("Início", "M3,10L12,3L21,10V21H15V14H9V21H3Z")
private val ListIcon = symbol("Movimentações", "M4,4H20V6H4ZM4,11H20V13H4ZM4,18H20V20H4Z")
private val PeopleIcon = symbol("Pessoas", "M9,3A4,4 0,1 0,9,11A4,4 0,1 0,9,3M2,21V18C2,14 16,14 16,18V21ZM17,4A4,4 0,0 1,17,12V10A2,2 0,0 0,17,6ZM18,15C21,15 23,16 23,18V21H20V18C20,17 19,16 18,16Z")
private val PlanIcon = symbol("Planejamento", "M6,2H8V4H16V2H18V4H21V22H3V4H6ZM5,9V20H19V9ZM7,12H11V16H7Z")
private val WealthIcon = symbol("Patrimônio", "M3,20V4H5V18H22V20ZM8,16V11H11V16ZM13,16V7H16V16ZM18,16V3H21V16Z")
private val LockIcon = symbol("Proteção", "M6,10V7A6,6 0,0 1,18,7V10H20V22H4V10ZM8,10H16V7A4,4 0,0 0,8,7ZM11,14V18H13V14Z")
private val SettingsIcon = symbol("Configurações", "M3,6H14V4H18V6H21V8H18V10H14V8H3ZM3,16H6V14H10V16H21V18H10V20H6V18H3Z")
private val Sections = listOf(
    Section("Início", "Início", "Seu dinheiro, com clareza.", HomeIcon),
    Section("Movimentações", "Movimentos", "Contas, faturas e parcelas em um só lugar.", ListIcon),
    Section("Pessoas", "Pessoas", "Mais clareza nas contas compartilhadas.", PeopleIcon),
    Section("Planejamento", "Planejar", "Espaço para seus planos e prioridades.", PlanIcon),
    Section("Patrimônio", "Patrimônio", "Acompanhe o caminho até seus objetivos.", WealthIcon),
)

@Composable
internal fun FinanceApp(
    model: FinanceViewModel,
    onUnlock: () -> Unit,
    onRefresh: () -> Unit,
    onSecuritySettings: () -> Unit,
    deviceSecure: Boolean,
) {
    val state by model.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    FinanceTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                !state.visible -> Box(Modifier.fillMaxSize())
                !state.unlocked -> LockedScreen(state, deviceSecure, onUnlock, onSecuritySettings)
                else -> Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (!settings) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Sections.forEachIndexed { index, section ->
                                NavigationBarItem(
                                    selected = selected == index,
                                    onClick = { selected = index },
                                    icon = { Icon(section.icon, contentDescription = section.title) },
                                    label = { Text(section.navigation, maxLines = 1) },
                                )
                            }
                        }
                    },
                ) { insets ->
                    Column(
                        Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        if (settings) {
                            TextButton(onClick = { settings = false }, contentPadding = PaddingValues(0.dp)) {
                                Text("Voltar")
                            }
                            ScreenTitle("Configurações", "Um espaço só seu.")
                            SecurityCard(state.createdAtMillis)
                            InfoCard("Google e recuperação", "Use Atualizar pelo Gmail em Movimentações para autorizar a leitura dos seus extratos. Os dados ficam cifrados neste aparelho; backup e restauração ainda estão pendentes.")
                            OutlinedButton(onClick = { settings = false; model.lock() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Bloquear agora")
                            }
                            Text("Meu Financeiro · ${br.com.yurimedeiros.meufinanceiro.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("MEU FINANCEIRO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { settings = true }) {
                                    Icon(SettingsIcon, contentDescription = "Configurações")
                                }
                            }
                            val section = Sections[selected]
                            ScreenTitle(if (selected == 0) "Tudo começa\ncom clareza." else section.title, section.description)
                            if (selected == 0) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp)) {
                                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Icon(LockIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                                        Text("Seu espaço está protegido", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("A base do seu controle financeiro está pronta. Seus saldos e orçamentos aparecerão aqui quando as próximas funções estiverem disponíveis.", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                                SecurityCard(state.createdAtMillis)
                                InfoCard("Seu próximo passo", "Abra Movimentações para atualizar seus extratos pelo Gmail, revisar categorias e confirmar os registros.")
                            } else if (selected == 1) {
                                MovementsScreen(state, model, onRefresh)
                            } else {
                                EmptySection(section)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedScreen(state: FinanceUiState, deviceSecure: Boolean, onUnlock: () -> Unit, onSecuritySettings: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(LockIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
            }
            Text("Meu Financeiro", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Seu dinheiro. Seu espaço.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text("Use sua biometria ou o PIN/senha do Android para continuar.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            state.error?.let { Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium) }
            if (state.opening) {
                CircularProgressIndicator(Modifier.size(32.dp))
                Text("Abrindo seu espaço…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Button(onClick = if (deviceSecure) onUnlock else onSecuritySettings, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(if (deviceSecure) "Desbloquear" else "Configurar bloqueio do Android")
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SecurityCard(createdAtMillis: Long?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Cuidado com seus dados", style = MaterialTheme.typography.titleMedium)
            Text("Acesso por biometria ou credencial do Android", style = MaterialTheme.typography.bodyMedium)
            Text("Armazenamento criptografado neste aparelho", style = MaterialTheme.typography.bodyMedium)
            Text("Novo desbloqueio após 1 minuto fora do app", style = MaterialTheme.typography.bodyMedium)
            createdAtMillis?.let {
                Text("Espaço criado em ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptySection(section: Section) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(section.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Cada coisa no seu lugar", style = MaterialTheme.typography.titleLarge)
            Text("Esta área será preenchida nas próximas etapas. Consulte seus registros importados em Movimentações.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
