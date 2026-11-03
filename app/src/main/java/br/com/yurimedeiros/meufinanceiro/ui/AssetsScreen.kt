package br.com.yurimedeiros.meufinanceiro.ui

import br.com.yurimedeiros.meufinanceiro.formatting.AppDates

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
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
internal fun BitcoinQuoteButton(loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val accent = NeonPalette[0]
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.heightIn(min = 48.dp).semantics {
            contentDescription = if (loading) "Atualizando cotação do Bitcoin" else "Atualizar cotação do Bitcoin"
        },
        shape = CircleShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = accent.copy(alpha = 0.12f), contentColor = accent,
            disabledContainerColor = accent.copy(alpha = 0.06f), disabledContentColor = accent.copy(alpha = 0.6f),
        ),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
        else Icon(RefreshIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("BTC", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun AssetsScreen(state: FinanceUiState, model: FinanceViewModel) {
    LaunchedEffect(state.unlocked, state.visible, state.ledger.assets.assets.map { it.id }) {
        if (state.unlocked && state.visible) {
            while (true) { model.refreshBitcoin(); kotlinx.coroutines.delay(300_000L) }
        }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.bitcoinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.bitcoinNotice?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    val book = state.ledger.assets
    var adding by remember { mutableStateOf(false) }
    var goalOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val live = book.assets.filter { !it.disposed }
    val known = live.mapNotNull { it.valueCents }.sum()
    val incomplete = live.any { it.valueCents == null }
    GlassPanel(highlighted = true) {
        Text("Patrimônio registrado", style = MaterialTheme.typography.titleSmall)
        Text(ledgerMoney(known), style = MaterialTheme.typography.headlineLarge)
        Text(if (incomplete) "Parcial: há ativos sem cotação." else "Fora do saldo bancário e do Flash", style = MaterialTheme.typography.bodySmall)
    }
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
    val goalPartial = selectedValues.any { it == null } || (book.goal.includeBank && planning.balance == null) || (book.goal.includeFlash && flash == null)
    val progress = goalValues.filterNotNull().sum()
    GlassPanel {
        Text("Sua meta · ${ledgerMoney(book.goal.targetCents)}", style = MaterialTheme.typography.titleSmall)
        Text("${ledgerMoney(progress)}${if (goalPartial) " · parcial" else ""}")
        LinearProgressIndicator(progress = { (progress.toDouble() / book.goal.targetCents).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { goalOpen = true }) { Text("Escolher componentes") }
    }
    OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar patrimônio") }
    if (book.assets.isEmpty()) Text("Cadastre apenas os saldos e bens que você possui.")
    book.assets.forEach { asset -> GlassPanel {
        Text(asset.name, style = MaterialTheme.typography.titleMedium)
        Text(if (asset.disposed) "Vendido · histórico preservado" else asset.valueCents?.let(::ledgerMoney) ?: "Cotação ainda indisponível")
        Text("${assetLabel(asset.kind)} · confirmado em ${AppDates.date(asset.confirmedDate)}", style = MaterialTheme.typography.bodySmall)
        if (asset.kind == AssetKind.BITCOIN) Text("${btcEdit(asset.satoshis)} BTC", style = MaterialTheme.typography.bodySmall)
        asset.quotedAt?.let { Text("${asset.quoteSource} · ${AppDates.timestamp(it)}", style = MaterialTheme.typography.labelSmall) }
        TextButton(onClick = { selected = asset.id }) { Text("Ver detalhes") }
    } }
    if (adding) AddAssetDialog(state, model) { adding = false }
    selected?.let { id -> book.assets.firstOrNull { it.id == id }?.let { AssetDetails(it, state, model) { selected = null } } }
    if (goalOpen) {
        var target by remember { mutableStateOf(assetEdit(book.goal.targetCents)) }
        var selectedIds by remember { mutableStateOf(book.goal.selectedAssets) }
        var bank by remember { mutableStateOf(book.goal.includeBank) }
        var flashChosen by remember { mutableStateOf(book.goal.includeFlash) }
        var receivables by remember { mutableStateOf(book.goal.includeReceivables) }
        var error by remember { mutableStateOf<String?>(null) }
        LedgerDialog("Componentes da meta", state, { goalOpen = false }, confirm = {
            try { model.saveLedger(LedgerAction.Asset(AssetChange.Goal(GoalSettings(FinanceLedger.money(target), selectedIds, bank, flashChosen, receivables)))) { goalOpen = false } }
            catch (_: Exception) { error = "Informe uma meta positiva." }
        }) {
            LedgerField("Meta", target) { target = it }
            book.assets.forEach { asset -> OutlinedButton(onClick = { selectedIds = if (asset.id in selectedIds) selectedIds - asset.id else selectedIds + asset.id }) { Text("${if (asset.id in selectedIds) "✓ " else ""}${asset.name}") } }
            OutlinedButton(onClick = { bank = !bank }) { Text("${if (bank) "✓ " else ""}Saldo bancário") }
            OutlinedButton(onClick = { flashChosen = !flashChosen }) { Text("${if (flashChosen) "✓ " else ""}Flash") }
            OutlinedButton(onClick = { receivables = !receivables }) { Text("${if (receivables) "✓ " else ""}Valores a receber") }
            Text("Esta seleção altera apenas a meta. Flash e valores a receber começam desmarcados e não passam a integrar o disponível para gastar.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AddAssetDialog(state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
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
                Text("NOVO PATRIMÔNIO", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("Adicionar patrimônio", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Organize seus bens e investimentos.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (kind !in setOf(AssetKind.FGTS, AssetKind.BITCOIN)) {
                        AssetEntryField("Nome", name, !state.syncing) { name = it }
                    }
                    AssetEntryField(if (kind == AssetKind.BITCOIN) "Quantidade de bitcoin" else "Valor confirmado",
                        amount, !state.syncing) { amount = it }
                    AssetEntryField("Data da confirmação", date, !state.syncing) { date = it }
                    Text("Este registro não movimenta seu saldo bancário.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall) }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        try {
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
                    Text(if (state.syncing) "Salvando…" else "Adicionar patrimônio")
                }
                TextButton(onClick = close, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AssetEntryField(label: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = change, enabled = enabled,
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
private fun AssetDetails(asset: AssetPosition, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var action by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LedgerDialog(asset.name, state, close) {
        Text(asset.valueCents?.let(::ledgerMoney) ?: "Sem cotação")
        Text("Saldo confirmado em ${AppDates.date(asset.confirmedDate)}", style = MaterialTheme.typography.bodySmall)
        if (!asset.disposed) {
            if (asset.kind !in setOf(AssetKind.FGTS, AssetKind.BITCOIN)) Button(onClick = { action = "confirm" }) { Text("Confirmar saldo real") }
            OutlinedButton(onClick = { action = "transfer" }) { Text("Aporte, resgate ou venda") }
            if (asset.kind == AssetKind.FGTS) OutlinedButton(onClick = { action = "configure" }) { Text("Configurar") }
        }
        if (asset.kind == AssetKind.FGTS) {
            Text("Depósito previsto no dia 20 · ${ledgerMoney(asset.monthlyFgts)} · ${if (asset.fgtsDepositsActive) "ativo" else "interrompido"}", style = MaterialTheme.typography.bodySmall)
            Text("Saldo exibido é a última confirmação real. Rendimentos futuros dependem de metodologia validada; nenhuma taxa fixa foi aplicada.", style = MaterialTheme.typography.bodySmall)
        }
        Text("Histórico", style = MaterialTheme.typography.titleSmall)
        state.ledger.assets.history.filter { change -> when (change) {
            is AssetChange.Register -> change.asset.id == asset.id
            is AssetChange.Confirm -> change.id == asset.id
            is AssetChange.Configure -> change.id == asset.id
            is AssetChange.Transfer -> change.id == asset.id
            is AssetChange.Quote -> change.id == asset.id
            is AssetChange.Goal -> false
        } }.takeLast(30).reversed().forEach { change -> Text(when (change) {
            is AssetChange.Register -> "${AppDates.date(change.asset.confirmedDate)} · saldo inicial ${ledgerMoney(change.asset.cents)}"
            is AssetChange.Confirm -> "${AppDates.date(change.date)} · confirmação ${if (asset.kind == AssetKind.BITCOIN) btcEdit(change.satoshis) + " BTC" else ledgerMoney(change.cents)}"
            is AssetChange.Transfer -> "${AppDates.date(change.date)} · ${change.kind} ${ledgerMoney(change.bankCents)} · custos ${ledgerMoney(change.feesCents)}"
            is AssetChange.Quote -> "${AppDates.timestamp(change.timestamp)} · avaliação ${change.source}"
            is AssetChange.Configure -> "Configuração atualizada; versão anterior preservada"
            is AssetChange.Goal -> ""
        }, style = MaterialTheme.typography.bodySmall) }
    }
    if (action == "confirm") {
        var amount by remember { mutableStateOf(if (asset.kind == AssetKind.BITCOIN) btcEdit(asset.satoshis) else assetEdit(asset.cents)) }
        var date by remember { mutableStateOf(AppDates.date(LocalDate.now())) }
        LedgerDialog("Confirmar saldo real", state, { action = ""; error = null }, confirm = {
            try { model.saveLedger(LedgerAction.Asset(AssetChange.Confirm(asset.id, if (asset.kind == AssetKind.BITCOIN) 0 else FinanceLedger.money(amount), if (asset.kind == AssetKind.BITCOIN) AssetRules.satoshis(amount) else 0, AppDates.parse(date)))) { action = "" } }
            catch (_: Exception) { error = "Revise valor e data da confirmação." }
        }) {
            LedgerField(if (asset.kind == AssetKind.BITCOIN) "Quantidade de bitcoin" else "Saldo real", amount) { amount = it }
            LedgerField("Data", date) { date = it }
            Text("A confirmação cria uma nova base, preservando saldos anteriores e operações no histórico.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (action == "configure") {
        var path by remember { mutableStateOf(asset.vehiclePath) }; var monthly by remember { mutableStateOf(assetEdit(asset.monthlyFgts)) }
        var deposits by remember { mutableStateOf(asset.fgtsDepositsActive) }
        LedgerDialog("Configurar ${assetLabel(asset.kind)}", state, { action = ""; error = null }, confirm = {
            try { model.saveLedger(LedgerAction.Asset(AssetChange.Configure(asset.id, path, FinanceLedger.money(monthly), deposits))) { action = "" } }
            catch (_: Exception) { error = "Revise os dados informados." }
        }) {
            if (asset.kind == AssetKind.FGTS) {
                LedgerField("Depósito mensal previsto", monthly) { monthly = it }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Prever depósitos", Modifier.weight(1f)); Switch(deposits, { deposits = it }) }
                Text("Depósitos previstos não são adicionados ao saldo real confirmado.", style = MaterialTheme.typography.bodySmall)
            } else {
                LedgerField("Identificação FIPE", path) { path = it }
                Text("Tipo/marca/modelo/ano são necessários à consulta. Nenhum valor é inventado a partir do nome do veículo.", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (action == "transfer" && asset.kind == AssetKind.FGTS) FgtsTransferDialog(asset, state, model) { action = "" }
    if (action == "transfer" && asset.kind != AssetKind.FGTS) AssetTransferDialog(asset, state, model) { action = "" }
}

@Composable
private fun AssetTransferDialog(asset: AssetPosition, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var kind by remember { mutableStateOf(AssetTransferKind.CONTRIBUTION) }; var movementId by remember { mutableLongStateOf(0) }
    var fees by remember { mutableStateOf("0,00") }; var quantity by remember { mutableStateOf("") }; var included by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sources = state.movements.filter { it.id !in state.ledger.usedMovementIds && it.status !in setOf("CONFLICT", "IGNORED") && it.row.kind == DocumentKind.NUBANK_ACCOUNT && !PaymentMatching.isPayment(it.row) && if (kind == AssetTransferKind.CONTRIBUTION) it.row.cents < 0 else it.row.cents > 0 }
    val source = sources.firstOrNull { it.id == movementId }
    LedgerDialog("Vincular operação patrimonial", state, close, confirm = {
        try {
            require(source != null) { "Selecione a movimentação bancária." }
            val satoshis = if (asset.kind == AssetKind.BITCOIN) AssetRules.satoshis(quantity) else 0
            model.saveLedger(LedgerAction.Asset(AssetChange.Transfer(asset.id, source.id, kind, abs(source.row.cents), FinanceLedger.money(fees), satoshis, source.row.date, included)), close)
        } catch (e: Exception) { error = e.message ?: "Revise os dados da operação." }
    }) {
        val options = if (asset.kind == AssetKind.VEHICLE) listOf(AssetTransferKind.CONTRIBUTION, AssetTransferKind.SALE) else listOf(AssetTransferKind.CONTRIBUTION, AssetTransferKind.REDEMPTION)
        fun label(k: AssetTransferKind) = when (k) { AssetTransferKind.CONTRIBUTION -> "Aporte / compra"; AssetTransferKind.REDEMPTION -> "Resgate / venda parcial"; AssetTransferKind.SALE -> "Venda do veículo" }
        LedgerChoice("Operação", label(kind), options.map { k -> label(k) to { kind = k; movementId = 0; included = false } })
        LedgerChoice("Extrato", source?.let { "${AppDates.date(it.row.date)} · ${ledgerMoney(it.row.cents)}" } ?: "Escolher", sources.map { m -> "${AppDates.date(m.row.date)} · ${ledgerMoney(m.row.cents)} · ${m.row.description}" to { movementId = m.id; included = kind != AssetTransferKind.SALE && m.row.date <= asset.confirmedDate } })
        LedgerField("Custos da operação", fees) { fees = it }
        if (asset.kind == AssetKind.BITCOIN) LedgerField("Quantidade de bitcoin", quantity) { quantity = it }
        if (kind != AssetTransferKind.SALE) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Já incluída no saldo confirmado", Modifier.weight(1f)); Switch(included, { included = it }) }
        Text(if (kind == AssetTransferKind.SALE) "A venda retira o veículo do patrimônio e preserva avaliações anteriores. A entrada líquida será contada uma única vez, pelo extrato." else "A operação será vinculada ao extrato, sem criar outra entrada ou saída nem consumir orçamento como despesa comum.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FgtsTransferDialog(asset: AssetPosition, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(AppDates.date(LocalDate.now())) }
    var contribution by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LedgerDialog("Aporte ou saque do FGTS", state, close, confirm = {
        try {
            model.saveLedger(LedgerAction.Asset(AssetChange.Transfer(asset.id, 0,
                if (contribution) AssetTransferKind.CONTRIBUTION else AssetTransferKind.REDEMPTION,
                FinanceLedger.money(amount), 0, 0, AppDates.parse(date), false)), close)
        } catch (_: Exception) { error = "Revise valor e data." }
    }) {
        LedgerChoice("Operação", if (contribution) "Aporte" else "Saque", listOf("Aporte" to { contribution = true }, "Saque" to { contribution = false }))
        LedgerField("Valor", amount) { amount = it }
        LedgerField("Data", date) { date = it }
        Text("Registra somente o FGTS. Uma entrada na conta será contabilizada pelo extrato bancário.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
