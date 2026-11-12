package br.com.yurimedeiros.meufinanceiro.ui

import br.com.yurimedeiros.meufinanceiro.formatting.AppDates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.assets.*
import br.com.yurimedeiros.meufinanceiro.importing.*
import br.com.yurimedeiros.meufinanceiro.ledger.*
import br.com.yurimedeiros.meufinanceiro.planning.PlanningRules
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

private fun assetLabel(kind: AssetKind) = when (kind) { AssetKind.INVESTMENT -> "Investimentos"; AssetKind.BITCOIN -> "Bitcoin"; AssetKind.FGTS -> "FGTS"; AssetKind.VEHICLE -> "Veículo" }
private fun assetEdit(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
private fun btcEdit(satoshis: Long) = BigDecimal.valueOf(satoshis, 8).toPlainString()

@Composable
internal fun AssetRefreshButton(loading: Boolean, enabled: Boolean,
    description: String = "Atualizar Bitcoin, investimentos e veículos pela FIPE", onClick: () -> Unit) {
    val accent = NeonPalette[0]
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.size(48.dp).semantics {
            contentDescription = if (loading) "Atualizando patrimônios" else description
        },
        shape = CircleShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = accent.copy(alpha = 0.12f), contentColor = accent,
            disabledContainerColor = accent.copy(alpha = 0.06f), disabledContentColor = accent.copy(alpha = 0.6f),
        ),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
        else Icon(RefreshIcon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun AssetsScreen(state: FinanceUiState, model: FinanceViewModel, onRefreshInvestments: () -> Unit,
    active: Boolean = true, onRouteChanged: () -> Unit = {}) {
    LaunchedEffect(state.unlocked, state.visible, state.ledger.assets.assets.map { it.id to it.vehiclePath }) {
        if (state.unlocked && state.visible) {
            while (true) { model.refreshBitcoin(); model.refreshVehicles(); kotlinx.coroutines.delay(300_000L) }
        }
    }
    val book = state.ledger.assets
    var adding by remember { mutableStateOf(false) }
    var goalOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<AssetKind?>(null) }
    var investmentsOpen by remember { mutableStateOf(false) }
    if (investmentsOpen) {
        fun back() { investmentsOpen = false; selected = null; onRouteChanged() }
        BackHandler(enabled = active && state.unlocked && state.visible && selected == null) { back() }
        InvestmentsScreen(state, onBack = { back() }, onRefresh = onRefreshInvestments, onAsset = { selected = it })
        selected?.let { id -> book.assets.firstOrNull { it.id == id && it.kind == AssetKind.INVESTMENT }?.let {
            AssetDetails(it, state, model) { selected = null }
        } }
        return
    }
    val live = book.assets.filter { !it.disposed }
    val known = live.mapNotNull { it.valueCents }.sum()
    val incomplete = live.any { it.valueCents == null }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Patrimônio", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        AssetRefreshButton(
            loading = state.syncing || state.quoting || state.quotingVehicles,
            enabled = state.unlocked && state.visible && state.statements.isEmpty() && state.custody.isEmpty(),
            onClick = {
                model.refreshBitcoin(manual = true)
                model.refreshVehicles(manual = true)
                onRefreshInvestments()
            },
        )
        FilledTonalIconButton(onClick = { adding = true }, enabled = !state.syncing,
            modifier = Modifier.size(48.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = NeonPalette[0].copy(alpha = 0.12f), contentColor = NeonPalette[0]),
        ) {
            Icon(AddAssetIcon, contentDescription = "Adicionar patrimônio", modifier = Modifier.size(22.dp))
        }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.vehicleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.vehicleNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    state.bitcoinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.bitcoinNotice?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    Text(ledgerMoney(known), style = MaterialTheme.typography.headlineLarge)
    if (incomplete) Text("Parcial: há ativos sem cotação.", style = MaterialTheme.typography.bodySmall)
    val planning = PlanningRules.view(state.ledger.planning, state.ledger, state.movements, LocalDate.now())
    val selectedValues = live.filter { it.id in book.goal.selectedAssets }.map { it.valueCents }
    val flashRows = state.movements.filter { it.row.kind == DocumentKind.FLASH }
    val latestFlash = flashRows.maxWithOrNull(compareBy<ReviewMovement> { it.row.date }.thenBy { it.row.time })
    val flash = latestFlash?.let { last -> flashRows.filter { it.row.date == last.row.date && it.row.time == last.row.time }.map { it.row.balanceCents }.distinct().singleOrNull() }
    val goalValues = selectedValues + listOfNotNull(
        if (book.goal.includeBank) planning.balance else null,
        if (book.goal.includeFlash) flash else null,
        if (book.goal.includeReceivables) state.ledger.debts.filter { it.direction == DebtDirection.RECEIVE }.sumOf { it.remaining } else null,
    )
    val progress = goalValues.filterNotNull().sum()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(
                progress = { (progress.toDouble() / book.goal.targetCents).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f),
            )
            Text(ledgerMoney(book.goal.targetCents), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = { goalOpen = true }) { Text("Alterar") }
    }
    AssetCharts(book)
    AssetGroupList(book, onGroup = { kind ->
        val vehicles = book.assets.filter { it.kind == AssetKind.VEHICLE }
        if (kind == AssetKind.INVESTMENT) {
            investmentsOpen = true; selected = null; selectedType = null; onRouteChanged()
        } else if (kind == AssetKind.VEHICLE && vehicles.size == 1) selected = vehicles.single().id
        else selectedType = kind
    }, onAsset = { selected = it })
    selectedType?.let { kind ->
        if (selected == null) AssetDialog(assetGroupLabel(kind), state, { selectedType = null },
            subtitle = "Selecione um patrimônio para ver os detalhes.") {
            book.assets.filter { it.kind == kind }.forEach { asset ->
                AssetRecordRow(asset, onClick = { selected = asset.id })
            }
        }
    }
    if (adding) AddAssetDialog(state, model) { adding = false }
    selected?.let { id -> book.assets.firstOrNull { it.id == id }?.let { AssetDetails(it, state, model) { selected = null } } }
    if (goalOpen) GoalDialog(state, model) { goalOpen = false }
}

@Composable
internal fun GoalDialog(state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    val book = state.ledger.assets
    var target by remember { mutableStateOf(assetEdit(book.goal.targetCents)) }
    var selectedIds by remember { mutableStateOf(book.goal.selectedAssets) }
    var expandedTypes by remember { mutableStateOf(emptySet<AssetKind>()) }
    var bank by remember { mutableStateOf(book.goal.includeBank) }
    var flashChosen by remember { mutableStateOf(book.goal.includeFlash) }
    var receivables by remember { mutableStateOf(book.goal.includeReceivables) }
    var error by remember { mutableStateOf<String?>(null) }
    AssetDialog("Meta", state, close, eyebrow = null, confirmLabel = "Salvar", confirm = {
        try { model.saveLedger(LedgerAction.Asset(AssetChange.Goal(GoalSettings(FinanceLedger.money(target), selectedIds, bank, flashChosen, receivables))), close) }
        catch (_: Exception) { error = "Informe uma meta positiva." }
    }) {
        AssetEntryField("Meta", target, !state.syncing, monetary = true) { target = it }
        Text("Composição do patrimônio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        GoalAssetTree(book, selectedIds, expandedTypes, !state.syncing,
            onSelectionChange = { selectedIds = it }, onExpandedChange = { expandedTypes = it })
        Text("Outros componentes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        AssetSelectionRow("Saldo bancário", bank, !state.syncing) { bank = it }
        AssetSelectionRow("Flash", flashChosen, !state.syncing) { flashChosen = it }
        AssetSelectionRow("Valores a receber", receivables, !state.syncing) { receivables = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun GoalAssetTree(
    book: AssetBook, selectedIds: Set<String>, expandedTypes: Set<AssetKind>, enabled: Boolean,
    onSelectionChange: (Set<String>) -> Unit, onExpandedChange: (Set<AssetKind>) -> Unit,
) {
    AssetKind.entries.forEach { kind ->
        val assets = book.assets.filter { it.kind == kind }
        if (assets.isNotEmpty()) {
            val ids = assets.map { it.id }.toSet()
            val selectedCount = ids.count { it in selectedIds }
            val checked = when (selectedCount) {
                0 -> ToggleableState.Off
                ids.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            val expanded = kind in expandedTypes
            val partial = assets.any { !it.disposed && it.valueCents == null }
            val amount = if (assets.all { it.valueCents == null }) "Sem cotação"
                else ledgerMoney(assets.mapNotNull { it.valueCents }.sum()) + if (partial) " · parcial" else ""
            Surface(shape = FinanceInputShape,
                color = if (selectedCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.025f),
                border = BorderStroke(1.dp, if (selectedCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TriStateCheckbox(state = checked, enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = "Selecionar todos: ${assetGroupLabel(kind)}" },
                        onClick = { onSelectionChange(if (checked == ToggleableState.On) selectedIds - ids else selectedIds + ids) })
                    Row(Modifier.weight(1f).heightIn(min = 64.dp)
                        .clickable(enabled = enabled, role = Role.Button,
                            onClickLabel = if (expanded) "Recolher ${assetGroupLabel(kind)}" else "Expandir ${assetGroupLabel(kind)}") {
                            onExpandedChange(if (expanded) expandedTypes - kind else expandedTypes + kind)
                        }.semantics { stateDescription = if (expanded) "Expandido" else "Recolhido" },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(assetGroupLabel(kind), style = MaterialTheme.typography.bodyMedium)
                            Text(amount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(AssetChevronIcon, contentDescription = null,
                            modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 0f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (expanded) Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                assets.forEach { asset ->
                    AssetSelectionRow(asset.name, asset.id in selectedIds, enabled,
                        detail = if (asset.disposed) "Vendido · ${ledgerMoney(0)}" else asset.valueCents?.let(::ledgerMoney) ?: "Sem cotação") {
                        onSelectionChange(if (it) selectedIds + asset.id else selectedIds - asset.id)
                    }
                }
            }
        }
    }
}

private fun assetGroupLabel(kind: AssetKind) = if (kind == AssetKind.VEHICLE) "Veículos" else assetLabel(kind)

@Composable
internal fun AssetGroupList(book: AssetBook, onGroup: (AssetKind) -> Unit, onAsset: (String) -> Unit) {
    val groups = AssetKind.entries.mapNotNull { kind ->
        book.assets.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { kind to it }
    }
    GlassPanel {
        Text("Patrimônios", style = MaterialTheme.typography.titleMedium)
        if (groups.isEmpty()) Text("Cadastre apenas os saldos e bens que você possui.")
        Column {
            groups.forEachIndexed { index, (kind, assets) ->
                val live = assets.filter { !it.disposed }
                val partial = live.any { it.valueCents == null }
                val amount = if (live.isNotEmpty() && live.all { it.valueCents == null }) "—"
                    else ledgerMoney(live.mapNotNull { it.valueCents }.sum()) + if (partial) " · parcial" else ""
                val multipleAllowed = kind == AssetKind.INVESTMENT || kind == AssetKind.VEHICLE || assets.size > 1
                val actionLabel = if (multipleAllowed) "Ver ${assetGroupLabel(kind).lowercase()}" else "Ver detalhes de ${assetGroupLabel(kind)}"
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = actionLabel) {
                        if (multipleAllowed) onGroup(kind) else onAsset(assets.single().id)
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(assetGroupLabel(kind), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(amount, Modifier.weight(1f).semantics {
                        if (amount == "—") contentDescription = "Cotação ainda indisponível"
                    }, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                    Icon(AssetChevronIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
                if (index < groups.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
internal fun AssetRecordRow(asset: AssetPosition, onClick: () -> Unit) {
    GlassPanel(modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Ver detalhes de ${asset.name}", onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(asset.name, style = MaterialTheme.typography.titleMedium)
                Text(if (asset.disposed) "Fora do patrimônio · histórico preservado" else asset.valueCents?.let(::ledgerMoney)
                    ?: "Cotação ainda indisponível")
                Text("Confirmado em ${AppDates.date(asset.confirmedDate)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(AssetChevronIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AddAssetDialog(state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var vehicleDialog by remember { mutableStateOf(false) }
    val vehicleId = remember { UUID.randomUUID().toString() }
    if (vehicleDialog) {
        VehicleFipeDialog(AssetPosition(vehicleId, "", AssetKind.VEHICLE, 0, 0, LocalDate.now()), state, model,
            { vehicleDialog = false; close() }, isNew = true)
        return
    }
    var name by remember { mutableStateOf("") }; var kind by remember { mutableStateOf(AssetKind.INVESTMENT) }
    var amount by remember { mutableStateOf("") }; var date by remember { mutableStateOf(AppDates.date(LocalDate.now())) }
    var error by remember { mutableStateOf<String?>(null) }; val id = remember { UUID.randomUUID().toString() }
    val availableKinds = AssetKind.entries.filter { candidate ->
        candidate !in setOf(AssetKind.FGTS, AssetKind.BITCOIN) ||
            state.ledger.assets.assets.none { it.kind == candidate }
    }
    // Keep the draft above the interaction gate so an automatic lock preserves it.
    if (LocalFinanceInteractive.current) Dialog(
        onDismissRequest = { if (!state.syncing) close() },
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0C162B),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(24.dp)) {
                Text("Adicionar patrimônio", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Tipo de patrimônio", style = MaterialTheme.typography.labelLarge)
                    // Two columns keep every type visible without a separate menu.
                    availableKinds.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { option ->
                                val selected = kind == option
                                OutlinedButton(
                                    onClick = { if (kind != option) { kind = option; amount = ""; error = null } },
                                    enabled = !state.syncing,
                                    shape = FinanceInputShape,
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else Color.Transparent,
                                        contentColor = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                        this.selected = selected
                                    },
                                ) { Text(assetLabel(option), style = MaterialTheme.typography.labelLarge) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (kind != AssetKind.VEHICLE) {
                        if (kind !in setOf(AssetKind.FGTS, AssetKind.BITCOIN)) {
                            AssetEntryField("Nome", name, !state.syncing) { name = it }
                        }
                        AssetEntryField(if (kind == AssetKind.BITCOIN) "Quantidade de bitcoin" else "Valor",
                            amount, !state.syncing, monetary = kind != AssetKind.BITCOIN) { amount = it }
                        AssetEntryField("Data da confirmação", date, !state.syncing) { date = it }
                    }
                    (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall) }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (kind == AssetKind.VEHICLE) vehicleDialog = true
                        else try {
                            val cents = if (kind == AssetKind.BITCOIN) 0 else FinanceLedger.money(amount)
                            val satoshis = if (kind == AssetKind.BITCOIN) AssetRules.satoshis(amount) else 0
                            model.saveLedger(LedgerAction.Asset(AssetChange.Register(AssetPosition(id,
                                if (kind in setOf(AssetKind.FGTS, AssetKind.BITCOIN)) assetLabel(kind) else name.trim(),
                                kind, cents, satoshis, AppDates.parse(date)))), close)
                        } catch (_: Exception) { error = "Revise nome, valor/quantidade e data." }
                    },
                    enabled = !state.syncing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = FinanceInputShape,
                ) {
                    if (state.syncing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.syncing) "Salvando…" else if (kind == AssetKind.VEHICLE) "Continuar" else "Adicionar patrimônio")
                }
                TextButton(onClick = close, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AssetEntryField(label: String, value: String, enabled: Boolean = true, monetary: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { if (monetary) normalizeMoneyInput(it)?.let(change) else change(it) }, enabled = enabled,
        visualTransformation = if (monetary) MoneyInputVisualTransformation else VisualTransformation.None,
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        shape = FinanceInputShape, singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White.copy(alpha = 0.025f),
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
internal fun AssetDialog(
    title: String, state: FinanceUiState, close: () -> Unit,
    subtitle: String? = null, confirmLabel: String = "Salvar", confirm: (() -> Unit)? = null,
    eyebrow: String? = null, confirmEnabled: Boolean = true,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalFinanceInteractive.current) Dialog(onDismissRequest = { if (!state.syncing) close() }) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF0C162B),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(24.dp)) {
                eyebrow?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    headerActions()
                }
                subtitle?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.height(20.dp))
                if (confirm != null) Button(onClick = confirm, enabled = !state.syncing && confirmEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = FinanceInputShape) {
                    if (state.syncing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.syncing) "Salvando…" else confirmLabel)
                }
                TextButton(onClick = close, enabled = !state.syncing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (confirm == null) "Fechar" else "Cancelar")
                }
            }
        }
    }
}

@Composable
private fun AssetSelectionRow(label: String, selected: Boolean, enabled: Boolean, detail: String? = null, change: (Boolean) -> Unit) {
    Surface(shape = FinanceInputShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.025f),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = change)
            .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun AssetChoice(label: String, value: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = FinanceInputShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("⌄", color = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = open && LocalFinanceInteractive.current, onDismissRequest = { open = false }) {
                options.forEach { (text, action) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { action(); open = false })
                }
                if (options.isEmpty()) DropdownMenuItem(text = { Text("Nenhuma movimentação disponível") },
                    onClick = {}, enabled = false)
            }
        }
    }
}

@Composable
private fun AssetInfoButton(description: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = description }) {
            Surface(shape = CircleShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), color = Color.Transparent) {
                Text("i", Modifier.size(22.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(max = 320.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
            }
        }
    }
}

@Composable
private fun VehicleValuationInfo(details: VehicleValuationDetails) {
    AssetInfoButton("Informações da cotação") {
        Text("Atualizado em ${details.updated}")
        Text("Fonte: ${details.source}")
        Text("Referência: ${details.reference}")
    }
}

@Composable
private fun FgtsDetailsInfo(asset: AssetPosition) {
    AssetInfoButton("Informações sobre o FGTS") {
        Text("Confirmado em ${AppDates.date(asset.confirmedDate)}")
        asset.quotedAt?.let { Text("${asset.quoteSource} · ${AppDates.timestamp(it)}") }
        Text("Depósito previsto no dia 20 · ${ledgerMoney(asset.monthlyFgts)} · ${if (asset.fgtsDepositsActive) "ativo" else "interrompido"}")
        Text("Saldo exibido é a última confirmação real. Rendimentos futuros dependem de metodologia validada; nenhuma taxa fixa foi aplicada.")
    }
}

@Composable
private fun AssetDetails(asset: AssetPosition, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var showHistory by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val valuation = VehicleValuationDetails.from(asset)
    if (action.isEmpty()) AssetDialog(asset.name, state, close, subtitle = assetLabel(asset.kind), headerActions = {
        when (asset.kind) {
            AssetKind.VEHICLE -> VehicleValuationInfo(valuation)
            AssetKind.FGTS -> FgtsDetailsInfo(asset)
            else -> Unit
        }
    }) {
        GlassPanel(highlighted = true) {
            Text(asset.valueCents?.let(::ledgerMoney) ?: "Sem cotação",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            if (asset.kind !in setOf(AssetKind.VEHICLE, AssetKind.FGTS)) Text("Confirmado em ${AppDates.date(asset.confirmedDate)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (asset.kind == AssetKind.BITCOIN) Text("${btcEdit(asset.satoshis)} BTC", style = MaterialTheme.typography.bodySmall)
            if (asset.kind == AssetKind.VEHICLE) Text(valuation.model, style = MaterialTheme.typography.bodySmall)
            else if (asset.kind != AssetKind.FGTS) asset.quotedAt?.let { Text("${asset.quoteSource} · ${AppDates.timestamp(it)}", style = MaterialTheme.typography.labelSmall) }
        }
        if (asset.kind == AssetKind.VEHICLE && !asset.disposed) {
            if (state.quotingVehicles) Text("Consultando FIPE…", style = MaterialTheme.typography.bodySmall)
            state.vehicleError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            state.vehicleNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (!asset.disposed) {
            if (asset.kind == AssetKind.INVESTMENT) Button(onClick = { action = "confirm" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = FinanceInputShape) { Text("Confirmar saldo real") }
            if (asset.kind == AssetKind.VEHICLE) OutlinedButton(onClick = { action = "configure" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = FinanceInputShape) { Text("Editar") }
            OutlinedButton(onClick = { action = if (asset.kind == AssetKind.VEHICLE) "remove" else "transfer" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = FinanceInputShape) { Text(if (asset.kind == AssetKind.VEHICLE) "Remover" else "Aporte ou resgate") }
            if (asset.kind == AssetKind.FGTS) OutlinedButton(onClick = { action = "configure" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = FinanceInputShape) { Text("Configurar") }
        }
        OutlinedButton(onClick = { showHistory = !showHistory }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = FinanceInputShape) { Text(if (showHistory) "Ocultar histórico" else "Histórico") }
        if (showHistory) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Histórico", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            state.ledger.assets.history.filter { change -> when (change) {
                is AssetChange.Register -> change.asset.id == asset.id
                is AssetChange.Confirm -> change.id == asset.id
                is AssetChange.Configure -> change.id == asset.id
                is AssetChange.Remove -> change.id == asset.id
                is AssetChange.Transfer -> change.id == asset.id
                is AssetChange.Quote -> change.id == asset.id
                is AssetChange.Goal -> false
            } }.reversed().forEach { change -> Text(when (change) {
                is AssetChange.Register -> "${AppDates.date(change.asset.confirmedDate)} · saldo inicial ${ledgerMoney(change.asset.cents)}"
                is AssetChange.Confirm -> "${AppDates.date(change.date)} · confirmação ${if (asset.kind == AssetKind.BITCOIN) btcEdit(change.satoshis) + " BTC" else ledgerMoney(change.cents)}"
                is AssetChange.Transfer -> "${AppDates.date(change.date)} · ${change.kind} ${ledgerMoney(change.bankCents)} · custos ${ledgerMoney(change.feesCents)}"
                is AssetChange.Quote -> "${AppDates.timestamp(change.timestamp)} · avaliação ${change.source}" + if (asset.kind == AssetKind.VEHICLE) " · ${ledgerMoney(change.cents)}" else ""
                is AssetChange.Configure -> "Configuração atualizada; versão anterior preservada"
                is AssetChange.Remove -> "${AppDates.date(change.date)} · veículo removido do patrimônio"
                is AssetChange.Goal -> ""
            }, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (action == "remove" && asset.kind == AssetKind.VEHICLE) {
        AssetDialog("Remover veículo", state, { action = "" }, confirmLabel = "Remover", confirm = {
            model.saveLedger(LedgerAction.Asset(AssetChange.Remove(asset.id, LocalDate.now())), close)
        }) {
            Text("Remover ${asset.name} do patrimônio? O histórico será preservado, sem vínculo com o extrato.")
        }
    }
    if (action == "confirm" && asset.kind == AssetKind.INVESTMENT) {
        var amount by remember { mutableStateOf(if (asset.kind == AssetKind.BITCOIN) btcEdit(asset.satoshis) else assetEdit(asset.cents)) }
        var date by remember { mutableStateOf(AppDates.date(LocalDate.now())) }
        AssetDialog("Confirmar saldo real", state, { action = ""; error = null }, confirm = {
            try { model.saveLedger(LedgerAction.Asset(AssetChange.Confirm(asset.id, if (asset.kind == AssetKind.BITCOIN) 0 else FinanceLedger.money(amount), if (asset.kind == AssetKind.BITCOIN) AssetRules.satoshis(amount) else 0, AppDates.parse(date)))) { action = "" } }
            catch (_: Exception) { error = "Revise valor e data da confirmação." }
        }) {
            AssetEntryField(if (asset.kind == AssetKind.BITCOIN) "Quantidade de bitcoin" else "Saldo real", amount, monetary = asset.kind != AssetKind.BITCOIN) { amount = it }
            AssetEntryField("Data", date) { date = it }
            Text("A confirmação cria uma nova base, preservando saldos anteriores e operações no histórico.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (action == "configure" && asset.kind == AssetKind.VEHICLE) VehicleFipeDialog(asset, state, model, { action = "" })
    if (action == "configure" && asset.kind == AssetKind.FGTS) {
        val path = asset.vehiclePath
        var monthly by remember { mutableStateOf(assetEdit(asset.monthlyFgts)) }
        var deposits by remember { mutableStateOf(asset.fgtsDepositsActive) }
        AssetDialog("Configurar ${assetLabel(asset.kind)}", state, { action = ""; error = null }, confirm = {
            try { model.saveLedger(LedgerAction.Asset(AssetChange.Configure(asset.id, path, FinanceLedger.money(monthly), deposits))) { action = "" } }
            catch (_: Exception) { error = "Revise os dados informados." }
        }) {
            AssetEntryField("Depósito mensal previsto", monthly, monetary = true) { monthly = it }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Prever depósitos", Modifier.weight(1f)); Switch(deposits, { deposits = it }) }
            Text("Depósitos previstos não são adicionados ao saldo real confirmado.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (action == "transfer" && asset.kind == AssetKind.FGTS) FgtsTransferDialog(asset, state, model) { action = "" }
    if (action == "transfer" && asset.kind != AssetKind.FGTS) AssetTransferDialog(asset, state, model) { action = "" }
}

@Composable
private fun AssetTransferDialog(asset: AssetPosition, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var kind by remember { mutableStateOf(if (asset.kind == AssetKind.VEHICLE) AssetTransferKind.SALE else AssetTransferKind.CONTRIBUTION) }; var movementId by remember { mutableLongStateOf(0) }
    var fees by remember { mutableStateOf("0,00") }; var quantity by remember { mutableStateOf("") }; var included by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sources = state.movements.filter { it.id !in state.ledger.usedMovementIds && it.status !in setOf("CONFLICT", "IGNORED") && it.row.kind == DocumentKind.NUBANK_ACCOUNT && !PaymentMatching.isPayment(it.row) && if (kind == AssetTransferKind.CONTRIBUTION) it.row.cents < 0 else it.row.cents > 0 }
    val source = sources.firstOrNull { it.id == movementId }
    AssetDialog("Vincular operação", state, close, subtitle = asset.name, confirmLabel = "Vincular operação", confirm = {
        try {
            require(source != null) { "Selecione a movimentação bancária." }
            val satoshis = if (asset.kind == AssetKind.BITCOIN) AssetRules.satoshis(quantity) else 0
            model.saveLedger(LedgerAction.Asset(AssetChange.Transfer(asset.id, source.id, kind, abs(source.row.cents), FinanceLedger.money(fees), satoshis, source.row.date, included)), close)
        } catch (e: Exception) { error = e.message ?: "Revise os dados da operação." }
    }) {
        val options = if (asset.kind == AssetKind.VEHICLE) listOf(AssetTransferKind.SALE) else listOf(AssetTransferKind.CONTRIBUTION, AssetTransferKind.REDEMPTION)
        fun label(k: AssetTransferKind) = when (k) { AssetTransferKind.CONTRIBUTION -> "Aporte / compra"; AssetTransferKind.REDEMPTION -> "Resgate / venda parcial"; AssetTransferKind.SALE -> "Venda do veículo" }
        AssetChoice("Operação", label(kind), options.map { k -> label(k) to { kind = k; movementId = 0; included = false } })
        AssetChoice("Extrato", source?.let { "${AppDates.date(it.row.date)} · ${ledgerMoney(it.row.cents)}" } ?: "Escolher", sources.map { m -> "${AppDates.date(m.row.date)} · ${ledgerMoney(m.row.cents)} · ${m.row.description}" to { movementId = m.id; included = kind != AssetTransferKind.SALE && m.row.date <= asset.confirmedDate } })
        AssetEntryField("Custos da operação", fees, monetary = true) { fees = it }
        if (asset.kind == AssetKind.BITCOIN) AssetEntryField("Quantidade de bitcoin", quantity) { quantity = it }
        if (kind != AssetTransferKind.SALE) AssetSelectionRow("Já incluída no saldo confirmado", included, !state.syncing) { included = it }
        Text(if (kind == AssetTransferKind.SALE) "A venda retira o veículo do patrimônio e preserva avaliações anteriores. A entrada líquida será contada uma única vez, pelo extrato." else "Vincula ao extrato sem duplicar movimentações ou consumir o orçamento.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FgtsTransferDialog(asset: AssetPosition, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(AppDates.date(LocalDate.now())) }
    var contribution by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    AssetDialog("Aporte ou saque do FGTS", state, close, headerActions = {
        AssetInfoButton("Informações da movimentação do FGTS") {
            Text("Registra somente o FGTS. Uma entrada na conta será contabilizada pelo extrato bancário.")
        }
    }, confirm = {
        try {
            model.saveLedger(LedgerAction.Asset(AssetChange.Transfer(asset.id, 0,
                if (contribution) AssetTransferKind.CONTRIBUTION else AssetTransferKind.REDEMPTION,
                FinanceLedger.money(amount), 0, 0, AppDates.parse(date), false)), close)
        } catch (_: Exception) { error = "Revise valor e data." }
    }) {
        AssetChoice("Operação", if (contribution) "Aporte" else "Saque", listOf("Aporte" to { contribution = true }, "Saque" to { contribution = false }))
        AssetEntryField("Valor", amount, monetary = true) { amount = it }
        AssetEntryField("Data", date) { date = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
