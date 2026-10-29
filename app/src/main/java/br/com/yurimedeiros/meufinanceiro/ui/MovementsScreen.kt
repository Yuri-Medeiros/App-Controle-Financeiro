package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.importing.*
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun MovementsScreen(state: FinanceUiState, model: FinanceViewModel) {
    val selected = remember { mutableStateMapOf<Long, Pair<String, String>>() }
    LaunchedEffect(state.movements) { selected.clear() }
    var query by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<DocumentKind?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var accountsOpen by remember { mutableStateOf(false) }
    val accountShape = FinanceInputShape
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { accountsOpen = !accountsOpen },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clip(accountShape).glassSurface(highlighted = accountsOpen),
                shape = accountShape,
                border = BorderStroke(1.dp, GlassBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text("Conta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(account?.let(::accountName) ?: "Todas as contas")
                }
                Text(if (accountsOpen) "⌃" else "⌄", color = NeonPalette[0])
            }
            DropdownMenu(
                expanded = accountsOpen,
                onDismissRequest = { accountsOpen = false },
                modifier = Modifier.width(maxWidth).background(neonGradient(0.16f), accountShape),
                shape = accountShape,
                containerColor = Color(0xFF080F24).copy(alpha = 0.94f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, GlassBorder),
            ) {
                AccountOptions.forEach { kind ->
                    val isSelected = account == kind
                    DropdownMenuItem(
                        text = {
                            Text(
                                kind?.let(::accountName) ?: "Todas as contas",
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) NeonPalette[0] else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        leadingIcon = {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = NeonPalette[0],
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        },
                        onClick = { account = kind; accountsOpen = false },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) NeonPalette[0].copy(alpha = 0.12f) else Color.Transparent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
        IconToggleButton(
            checked = searchVisible,
            onCheckedChange = { visible ->
                searchVisible = visible
                if (!visible) {
                    query = ""
                    focusManager.clearFocus()
                }
            },
            modifier = Modifier.size(56.dp).clip(accountShape)
                .glassSurface(highlighted = searchVisible)
                .border(BorderStroke(1.dp, GlassBorder), accountShape),
        ) {
            Icon(
                MovementFilterIcon,
                contentDescription = if (searchVisible) "Ocultar busca" else "Mostrar busca",
                tint = if (searchVisible) NeonPalette[0] else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
    if (searchVisible) {
        OutlinedTextField(
            query, { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar por nome ou data") },
            shape = accountShape, singleLine = true,
        )
    }
    if (state.syncing) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Consultando seus extratos…", style = MaterialTheme.typography.bodySmall)
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.statements.firstOrNull()?.let { statement ->
        var reference by remember(statement.source.source) { mutableStateOf(statement.document.proposedStatement?.toString().orEmpty()) }
        GlassPanel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Confirme a referência da fatura")
                Text(statement.source.filename)
                OutlinedTextField(reference, { reference = it }, label = { Text("Referência AAAA-MM-DD") }, shape = FinanceInputShape)
                GlassButton(onClick = { model.confirmStatement(reference) }, enabled = !state.syncing) { Text("Confirmar referência") }
            }
        }
    }
    if (selected.isNotEmpty()) {
        Text("Confirmar ${selected.size} registros com estas categorias:")
        selected.forEach { (id, choice) ->
            Text("${state.movements.firstOrNull { it.id == id }?.row?.description.orEmpty()} · ${choice.first}")
        }
        GlassButton(onClick = { model.confirmBatch(selected.toMap()) }, enabled = !state.syncing) { Text("Confirmar selecionados") }
    }
    if (state.movements.isNotEmpty()) {
        Text("${state.movements.count { it.status !in setOf("CONFIRMED", "PAYMENT") }} pendentes")
    }
    // Render a bounded page, keeping financial data out of savedInstanceState.
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, account) { page = 0 }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/uuuu") }
    val rows = state.movements.filter {
        (account == null || it.row.kind == account) &&
            (query.isBlank() || it.friendlyName.contains(query, ignoreCase = true) ||
                it.row.description.contains(query, ignoreCase = true) ||
                it.row.date.format(dateFormat).contains(query) || it.row.date.toString().contains(query))
    }.let { filtered ->
        // Order the entire result before pagination so review records always come first.
        val (pending, completed) = filtered.partition { it.status !in setOf("CONFIRMED", "PAYMENT") }
        pending.sortedBy { it.row.date } + completed.sortedByDescending { it.row.date }
    }
    val pages = ((rows.size + 29) / 30).coerceAtLeast(1)
    val current = page.coerceAtMost(pages - 1)
    if (rows.isEmpty() && state.movements.isNotEmpty()) Text("Nenhuma movimentação encontrada para estes filtros.", style = MaterialTheme.typography.bodyMedium)
    val visibleRows = rows.drop(current * 30).take(30)
    visibleRows.forEachIndexed { index, movement ->
        if (index == 0 || visibleRows[index - 1].row.date != movement.row.date ||
            (visibleRows[index - 1].status in setOf("CONFIRMED", "PAYMENT")) !=
            (movement.status in setOf("CONFIRMED", "PAYMENT"))) {
            Text(movement.row.date.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' uuuu", Locale.forLanguageTag("pt-BR"))), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    var category by remember(movement.category, previous?.category) { mutableStateOf(movement.category.ifBlank { previous?.category.orEmpty() }) }
    val identity = FriendlyDescriptions.key(movement.row)
    val linkedName = state.movements.filter {
        it.friendlyName.isNotBlank() &&
            it.origin.substringBefore(" · ") == movement.origin.substringBefore(" · ") &&
            FriendlyDescriptions.key(it.row) == identity
    }.maxWithOrNull(compareBy<ReviewMovement> { it.confirmedAt }.thenBy { it.id })?.friendlyName.orEmpty()
    val friendly = linkedName.ifBlank { movement.friendlyName }
    var descriptionOpen by remember { mutableStateOf(false) }
    var descriptionDraft by remember(friendly) { mutableStateOf(friendly) }
    var details by remember { mutableStateOf(false) }
    LaunchedEffect(category, friendly) { if (selected) onSelect(if (category.isBlank()) null else category to friendly) }
    GlassPanel {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val expense = if (movement.row.kind == DocumentKind.NUBANK_CARD) movement.row.cents > 0 else movement.row.cents < 0
            val amountColor = if (expense) ExpenseColor else IncomeColor
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).background(amountColor.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (expense) "↓" else "↑", color = amountColor, style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(friendly.ifBlank { suggestion.displayName }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(movement.category.ifBlank { accountName(movement.row.kind) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(movement.row.cents, 2)), color = amountColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(movement.row.date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    movement.row.time?.let { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Text(when (movement.status) {
                "CONFIRMED" -> "Confirmada · ${movement.category}"
                "PAYMENT" -> "Pagamento de fatura conciliado"
                "PAYMENT_PENDING" -> "Pagamento de fatura · conciliação pendente"
                else -> "Aguardando revisão"
            })
            if (details) {
                if (movement.status == "CONFLICT" || MovementRepository.needsSpecialReview(movement.row)) {
                    Text("Requer conciliação antes de categorizar.", style = MaterialTheme.typography.bodySmall)
                } else {
                    CategoryDropdown(category, { category = it }, enabled = !state.syncing)
                    if (movement.status != "CONFIRMED") Row {
                        Checkbox(checked = selected, onCheckedChange = { onSelect(if (it && category.isNotBlank()) category to friendly else null) }, enabled = category.isNotBlank() && !state.syncing)
                        Text("Incluir no lote · $category")
                    }
                    GlassButton(onClick = { model.confirmMovement(setOf(movement.id), category, friendly) }, enabled = category.isNotBlank() && !state.syncing) { Text("Pronto") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (friendly.isBlank()) {
                    IconButton(
                        onClick = { descriptionDraft = ""; descriptionOpen = true },
                        enabled = !state.syncing,
                    ) {
                        Icon(MovementPencilIcon, contentDescription = "Adicionar descrição amigável",
                            tint = NeonPalette[0], modifier = Modifier.size(22.dp))
                    }
                }
                IconButton(onClick = { details = !details }, enabled = !state.syncing) {
                    Icon(MovementLabelIcon,
                        contentDescription = if (details) "Fechar categoria" else "Informar categoria",
                        tint = NeonPalette[0], modifier = Modifier.size(22.dp))
                }
            }
        }
    }
    if (descriptionOpen) {
        AlertDialog(
            onDismissRequest = { descriptionOpen = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF080F24),
            tonalElevation = 0.dp,
            modifier = Modifier.border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(22.dp)),
            title = { Text("Descrição amigável") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Use um nome para identificar esta pessoa, estabelecimento ou descrição nas próximas movimentações. O texto original do extrato será preservado.")
                    OutlinedTextField(
                        descriptionDraft, { descriptionDraft = it },
                        label = { Text("Descrição amigável") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = descriptionDraft.trim().length > 120,
                        supportingText = { Text("Até 120 caracteres") },
                        shape = FinanceInputShape,
                    )
                }
            },
            confirmButton = {
                GlassButton(
                    onClick = {
                        model.saveFriendlyName(movement.id, descriptionDraft)
                        descriptionOpen = false
                    },
                    enabled = !state.syncing && descriptionDraft.isNotBlank() &&
                        descriptionDraft.trim().length <= 120,
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { descriptionOpen = false }) { Text("Cancelar") }
            },
        )
    }
}


private val CategoryOptions = listOf(
    "Moradia", "Alimentação", "Transporte", "Saúde", "Pessoal e lazer",
    "Educação", "Presentes e doações", "Tarifas e impostos",
)

@Composable
private fun CategoryDropdown(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }
    val options = (CategoryOptions + listOfNotNull(value.takeIf { it.isNotBlank() })).distinct()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clip(FinanceInputShape).glassSurface(highlighted = expanded),
            shape = FinanceInputShape,
            border = BorderStroke(1.dp, GlassBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("Categoria", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value.ifBlank { "Selecionar categoria" })
            }
            Text(if (expanded) "⌃" else "⌄", color = NeonPalette[0])
        }
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(maxWidth).background(neonGradient(0.16f), FinanceInputShape),
            shape = FinanceInputShape,
            containerColor = Color(0xFF080F24).copy(alpha = 0.94f),
            tonalElevation = 0.dp, shadowElevation = 0.dp,
            border = BorderStroke(1.dp, GlassBorder),
        ) {
            options.forEach { option ->
                val chosen = value == option
                DropdownMenuItem(
                    text = { Text(option, color = if (chosen) NeonPalette[0] else MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = {
                        RadioButton(chosen, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = NeonPalette[0]))
                    },
                    onClick = { onValueChange(option); expanded = false },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (chosen) NeonPalette[0].copy(alpha = 0.12f) else Color.Transparent),
                )
            }
            DropdownMenuItem(
                text = { Text("Outra categoria…", color = NeonPalette[0]) },
                onClick = { expanded = false; custom = ""; customOpen = true },
            )
        }
    }
    if (customOpen) {
        AlertDialog(
            onDismissRequest = { customOpen = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF080F24),
            tonalElevation = 0.dp,
            modifier = Modifier.border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(22.dp)),
            title = { Text("Outra categoria") },
            text = {
                OutlinedTextField(
                    custom, { custom = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome da categoria") }, shape = FinanceInputShape, singleLine = true,
                )
            },
            confirmButton = {
                GlassButton(
                    onClick = { onValueChange(custom.trim()); customOpen = false },
                    enabled = enabled && custom.isNotBlank(),
                ) { Text("Usar categoria") }
            },
            dismissButton = { TextButton(onClick = { customOpen = false }) { Text("Cancelar") } },
        )
    }
}

private val MovementPencilIcon = ImageVector.Builder(
    "Descrição amigável", 24.dp, 24.dp, 24f, 24f,
).addPath(
    PathParser().parsePathString("M3,17.25V21H6.75L17.81,9.94L14.06,6.19ZM20.71,7.04C21.1,6.65 21.1,6.02 20.71,5.63L18.37,3.29C17.98,2.9 17.35,2.9 16.96,3.29L15.13,5.12L18.88,8.87Z").toNodes(),
    fill = SolidColor(Color.Black),
).build()

private val MovementLabelIcon = ImageVector.Builder(
    "Categoria", 24.dp, 24.dp, 24f, 24f,
).addPath(
    PathParser().parsePathString("M21.41,11.58L12.41,2.58C12.04,2.21 11.53,2 11,2H4C2.9,2 2,2.9 2,4V11C2,11.53 2.21,12.04 2.59,12.41L11.59,21.41C12.37,22.19 13.63,22.19 14.41,21.41L21.41,14.41C22.19,13.63 22.19,12.37 21.41,11.58ZM5.5,7A1.5,1.5 0,1 1,5.5,4A1.5,1.5 0,1 1,5.5,7Z").toNodes(),
    fill = SolidColor(Color.Black),
).build()

private val MovementFilterIcon = ImageVector.Builder(
    "Filtros", 24.dp, 24.dp, 24f, 24f,
).addPath(
    PathParser().parsePathString("M3,4H21L14,12V19L10,21V12Z").toNodes(),
    fill = SolidColor(Color.Black),
).build()

private val AccountOptions = listOf(
    null,
    DocumentKind.NUBANK_ACCOUNT,
    DocumentKind.NUBANK_CARD,
    DocumentKind.FLASH,
)

private fun accountName(kind: DocumentKind): String = when (kind) {
    DocumentKind.NUBANK_ACCOUNT -> "Nubank Conta"
    DocumentKind.NUBANK_CARD -> "Nubank Fatura"
    DocumentKind.FLASH -> "Flash"
}
