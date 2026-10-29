package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import br.com.yurimedeiros.meufinanceiro.R
import androidx.compose.ui.graphics.Brush
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
private val RefreshIcon = symbol("Atualizar", "M20,7V2L18,4A9,9 0,1 0,21,14H19A7,7 0,1 1,16.5,5.5L14,8H20Z")
private val SettingsIcon = symbol("Configurações", "M3,6H14V4H18V6H21V8H18V10H14V8H3ZM3,16H6V14H10V16H21V18H10V20H6V18H3Z")
private val Sections = listOf(
    Section("Resumo", "Resumo", "Seu dinheiro, com clareza.", HomeIcon),
    Section("Movimentações", "Movimentações", "Contas, faturas e parcelas em um só lugar.", ListIcon),
    Section("Pessoas", "Pessoas", "Mais clareza nas contas compartilhadas.", PeopleIcon),
    Section("Planejamento", "Planejamento", "Espaço para seus planos e prioridades.", PlanIcon),
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
    val contentScroll = remember(selected, settings) { ScrollState(0) }
    FinanceTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                !state.visible -> Box(Modifier.fillMaxSize())
                !state.unlocked -> LockedScreen(state, deviceSecure, onUnlock, onSecuritySettings)
                else -> Scaffold(
                    modifier = Modifier.background(Color(0xFF070D20)).background(neonGradient(0.28f)),
                    containerColor = Color.Transparent,
                    bottomBar = {
                        if (!settings) NavigationBar(
                            modifier = Modifier.padding(horizontal = 8.dp).background(neonGradient(0.13f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                            containerColor = Color(0xFF071124).copy(alpha = 0.72f),
                            tonalElevation = 0.dp,
                        ) {
                            Sections.forEachIndexed { index, section ->
                                NavigationBarItem(
                                    selected = selected == index,
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = NeonPalette[0].copy(alpha = 0.18f), selectedIconColor = NeonPalette[0], selectedTextColor = Color(0xFFE0FCFF), unselectedIconColor = Color(0xFFB9B4CE), unselectedTextColor = Color(0xFFB9B4CE)),
                                    onClick = { selected = index },
                                    icon = { Icon(section.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                                    label = { Text(section.navigation, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    },
                ) { insets ->
                    Column(
                        Modifier.fillMaxSize().padding(insets).verticalScroll(contentScroll).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                            val section = Sections[selected]
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(section.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                if (selected == 1) {
                                    IconButton(onClick = onRefresh, enabled = !state.syncing && state.statements.isEmpty()) {
                                        Icon(RefreshIcon, contentDescription = "Atualizar pelo Gmail")
                                    }
                                } else if (selected == 0) {
                                    IconButton(onClick = { settings = true }) {
                                        Icon(SettingsIcon, contentDescription = "Configurações")
                                    }
                                }
                            }
                            when (selected) {
                                0 -> OverviewScreen(state, onMovements = { selected = 1 })
                                1 -> MovementsScreen(state, model, onRefresh)
                                2 -> PlannedScreen("Pessoas")
                                3 -> PlannedScreen("Planejamento")
                                4 -> PlannedScreen("Patrimônio")
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
    Box(Modifier.fillMaxSize()) {
        LockBackground()
        Scaffold(containerColor = Color.Transparent) { insets ->
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
                state.error?.let { Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium) }
                if (state.opening) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Text("Abrindo seu espaço…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Button(onClick = if (deviceSecure) onUnlock else onSecuritySettings,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFFF4F1FF)),
                        contentPadding = PaddingValues(),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.fillMaxWidth().height(56.dp).background(
                            neonGradient(0.28f),
                            RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp)) {
                        Text(if (deviceSecure) "Acessar" else "Configurar bloqueio do Android")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SecurityCard(createdAtMillis: Long?) {
    GlassPanel {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
private fun LockBackground() {
    val transition = rememberInfiniteTransition(label = "Fundo do bloqueio")
    val drift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000), RepeatMode.Reverse),
        label = "Movimento suave",
    )
    Image(
        painter = painterResource(R.drawable.lock_background),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = 1.02f + drift * 0.02f
            scaleY = scaleX
        },
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
}
