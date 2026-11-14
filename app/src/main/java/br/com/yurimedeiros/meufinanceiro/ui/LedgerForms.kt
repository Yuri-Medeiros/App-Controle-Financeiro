package br.com.yurimedeiros.meufinanceiro.ui

import br.com.yurimedeiros.meufinanceiro.formatting.AppDates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.selected
import br.com.yurimedeiros.meufinanceiro.domain.PaymentDivision
import br.com.yurimedeiros.meufinanceiro.domain.DivisionMode
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.yurimedeiros.meufinanceiro.importing.*
import br.com.yurimedeiros.meufinanceiro.ledger.*
import br.com.yurimedeiros.meufinanceiro.planning.BankCalendar
import br.com.yurimedeiros.meufinanceiro.domain.PurchaseSplit
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

internal fun ledgerMoney(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents, 2))
private fun editMoney(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
internal fun personLabel(state: FinanceUiState, id: Long) = if (id == 0L) "Eu" else state.people.firstOrNull { it.id == id }?.name ?: "Pessoa não encontrada"

@Composable
internal fun LedgerField(label: String, value: String, monetary: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value, { if (monetary) normalizeMoneyInput(it)?.let(change) else change(it) },
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = FinanceInputShape, singleLine = true,
        visualTransformation = if (monetary) MoneyInputVisualTransformation else VisualTransformation.None)
}

@Composable
internal fun LedgerChoice(label: String, value: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = FinanceInputShape) { Text("$label: $value") }
        DropdownMenu(expanded = open && LocalFinanceInteractive.current, onDismissRequest = { open = false }) {
            options.forEach { (text, action) -> DropdownMenuItem(text = { Text(text) }, onClick = { action(); open = false }) }
        }
    }
}

@Composable
internal fun LedgerDialog(
    title: String, state: FinanceUiState, close: () -> Unit,
    confirmLabel: String = "Confirmar", confirm: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalFinanceInteractive.current) AppAlertDialog(
        onDismissRequest = { if (!state.syncing) close() }, title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { if (confirm != null) Button(onClick = confirm, enabled = !state.syncing) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = close, enabled = !state.syncing) { Text(if (confirm == null) "Fechar" else "Cancelar") } },
    )
}

@Composable
internal fun PurchaseDialog(movement: ReviewMovement, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    key(movement.id) { ImportedPurchaseEditor(movement, state, model, close) }
}

@Composable
private fun ImportedPurchaseEditor(movement: ReviewMovement, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    val numbering = runCatching { ImportedInstallments.schedule(movement.row.description) }
    val linkOptions = runCatching { ImportedInstallments.options(state.ledger, movement, state.movements) }.getOrDefault(emptyList())
    var title by remember { mutableStateOf(movement.friendlyName.ifBlank { movement.row.description }) }
    var editingTitle by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    var category by remember { mutableStateOf(movement.category) }
    val invoiceDue = runCatching { InvoiceSchedule.due(movement.row, state.ledger.planning.invoiceDates) }.getOrNull()
    var division by remember { mutableStateOf(PaymentDivision(movement.row.cents)) }
    val inputs = remember { mutableStateMapOf<Long, String>() }
    var draft by remember { mutableStateOf<PurchasePlan?>(null) }
    var chosenLink by remember { mutableStateOf<Pair<PurchasePlan, SchedulePart>?>(null) }
    var chooseLink by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val id = remember { UUID.randomUUID().toString() }
    val invalidInputs = remember { mutableStateMapOf<String, Boolean>() }
    val people = division.people
    val allocation = runCatching { division.amounts() }
    val validInputs = inputs.values.all { runCatching { FinanceLedger.money(it) }.isSuccess }
    fun resetPeople() { division = PaymentDivision(movement.row.cents); inputs.clear(); chosenLink = null; chooseLink = false; localError = null }
    fun dismiss() { resetPeople(); close() }
    fun updatePeople(next: List<Long>) {
        division = division.copy(people = next, fixed = division.fixed.filterKeys { it in next }, initial = null)
        inputs.keys.toList().filter { it !in next }.forEach { inputs.remove(it) }
    }
    fun edit(person: Long, value: String) {
        val normalized = normalizeMoneyInput(value) ?: return
        inputs[person] = normalized
        val units = runCatching { FinanceLedger.money(normalized) }.getOrNull()
        division = if (units == null) division.copy(fixed = division.fixed - person, initial = null) else division.edit(person, units)
    }
    fun record(action: LedgerAction) = model.saveNamedPurchase(action, movement.id, title.trim(), ::dismiss)
    fun save() {
        try {
            localError = null
            require(people.isNotEmpty()) { "Defina ao menos uma pessoa." }
            require(title.isNotBlank() && title.trim().length <= 120) { "Informe um título de até 120 caracteres." }
            require(validInputs) { "Preencha os valores da divisão." }
            val amounts = division.amounts()
            val link = chosenLink
            if (link != null) {
                require(linkOptions.any { it.first.id == link.first.id && it.second.number == link.second.number }) { "A parcela prevista não está mais disponível." }
                val action = InvoiceSchedule.linkAction(state.ledger, link.first.id, link.second.number, movement, people.zip(amounts).toMap())
                FinanceLedger.apply(state.ledger, action); record(action)
            } else if (draft == null) {
                if (category.isBlank()) { details = true; error("Escolha uma categoria.") }
                val number = numbering.getOrThrow()
                val parts = InvoiceSchedule.parts(movement.row, number.number, number.count, movement.id, state.ledger.planning.invoiceDates)
                val shares = people.zip(amounts).associate { (person, cents) -> person to List(parts.size) { cents } }
                val plan = PersonReconciliation.purchase(state.ledger, id, title.trim(), category.trim(), parts, shares)
                FinanceLedger.apply(state.ledger, LedgerAction.Purchase(plan))
                if (parts.size == 1 && people.none { it > 0 }) record(LedgerAction.Purchase(plan))
                else { draft = plan; invalidInputs.clear() }
            } else {
                require(invalidInputs.values.none { it }) { "Corrija os campos inválidos da prévia." }
                val plan = draft!!.copy(title = title.trim())
                FinanceLedger.apply(state.ledger, LedgerAction.Purchase(plan)); record(LedgerAction.Purchase(plan))
            }
        } catch (e: Exception) { localError = e.message ?: "Revise os valores e a divisão." }
    }
    val interactive = LocalFinanceInteractive.current
    AssetDialog(title, state, ::dismiss,
        titleContent = {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val titleStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                if (editingTitle) {
                    BasicTextField(title, { title = it }, modifier = Modifier.fillMaxWidth().focusRequester(titleFocus), singleLine = true,
                        textStyle = titleStyle, enabled = !state.syncing, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary))
                    LaunchedEffect(Unit) { titleFocus.requestFocus() }
                } else Text(title, style = titleStyle)
                Text(movement.row.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { editingTitle = !editingTitle; if (!editingTitle) focus.clearFocus() }, enabled = !state.syncing) {
                if (editingTitle) Text("✓", modifier = Modifier.semantics { contentDescription = "Concluir edição do título" }, color = MaterialTheme.colorScheme.primary)
                else Icon(EditTitleIcon, "Editar título", Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            }
        },
        confirmEnabled = people.isNotEmpty() && allocation.isSuccess && validInputs && title.isNotBlank() && title.trim().length <= 120 && numbering.isSuccess,
        confirmLabel = when { chosenLink != null -> "Vincular"; draft != null -> "Salvar"; (numbering.getOrNull()?.count ?: 1) > 1 || people.any { it > 0 } -> "Revisar valores"; else -> "Salvar" },
        confirm = ::save) {
        if (draft == null) {
            Text(ledgerMoney(movement.row.cents), style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Data de movimentação", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(AppDates.date(movement.row.date), style = MaterialTheme.typography.bodyMedium)
            }
            numbering.getOrNull()?.takeIf { ImportedInstallments.parse(movement.row.description) != null }?.let {
                Text("Parcela ${it.number}/${it.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (linkOptions.isNotEmpty()) Surface(shape = FinanceInputShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .06f)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(chosenLink != null, enabled = !state.syncing, role = Role.Checkbox, onValueChange = { checked ->
                    if (checked) { focus.clearFocus(); chooseLink = true } else resetPeople()
                }).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(chosenLink != null, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Vincular parcela", style = MaterialTheme.typography.bodyMedium)
                        chosenLink?.let { (plan, part) -> Text("${plan.title} · ${part.number}/${plan.parts.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vencimento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(invoiceDue?.let(AppDates::date) ?: "Referência ausente", style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Pessoas", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                listOf("=" to "Dividir igualmente", "%" to "Dividir por percentual", "R$" to "Informar valores").forEachIndexed { index, option ->
                    IconButton(onClick = { focus.clearFocus(); division = division.mode(DivisionMode.entries[index]); inputs.clear(); localError = null }, enabled = !state.syncing,
                        modifier = Modifier.size(44.dp).semantics { contentDescription = option.second; selected = division.mode.ordinal == index }) {
                        Text(option.first, style = MaterialTheme.typography.titleMedium,
                            color = if (division.mode.ordinal == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val amounts = allocation.getOrNull()
            val percentages = runCatching { division.percentages() }.getOrNull()
            people.forEachIndexed { index, person ->
                val fixed = person in division.fixed
                val color = when { allocation.isFailure && fixed -> MaterialTheme.colorScheme.error; fixed -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(personLabel(state, person), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.width(124.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val editableAmount = division.mode == DivisionMode.AMOUNT
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("R$ ", style = MaterialTheme.typography.bodyMedium, color = color)
                            BasicTextField(if (editableAmount && person in inputs) inputs.getValue(person) else amounts?.get(index)?.let(::editMoney).orEmpty(), { edit(person, it) },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Valor de ${personLabel(state, person)}" },
                                enabled = !state.syncing, readOnly = !editableAmount, singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = color, textAlign = TextAlign.End),
                                visualTransformation = MoneyInputVisualTransformation, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary))
                        }
                        val editablePercent = division.mode == DivisionMode.PERCENT
                        Row(Modifier.width(84.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(if (editablePercent && person in inputs) inputs.getValue(person) else percentages?.get(index)?.let(::editMoney).orEmpty(), { edit(person, it) },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Percentual de ${personLabel(state, person)}" },
                                enabled = !state.syncing, readOnly = !editablePercent, singleLine = true,
                                textStyle = MaterialTheme.typography.labelSmall.copy(color = color, textAlign = TextAlign.End),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary))
                            Text("%", style = MaterialTheme.typography.labelSmall, color = color)
                        }
                    }
                    if (chosenLink == null) IconButton(onClick = { updatePeople(people - person) }, enabled = !state.syncing && people.size > 1,
                        modifier = Modifier.size(32.dp).semantics { contentDescription = "Remover ${personLabel(state, person)}" }) { Text("×", style = MaterialTheme.typography.titleLarge) }
                }
            }
            if (allocation.isFailure) Text(allocation.exceptionOrNull()?.message ?: "Revise a divisão.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (chosenLink == null) {
                LedgerChoice("Adicionar pessoa", "Escolher", (listOf(0L) + state.people.map { it.id }).filter { it !in people }.map { person -> personLabel(state, person) to { updatePeople(people + person) } })
                TextButton(onClick = { details = !details }, contentPadding = PaddingValues(0.dp)) { Text("Categoria") }
                if (details) CategoryDropdown(category, { category = it }, !state.syncing)
            }
        } else {
            val p = draft!!
            Text("Prévia · ${p.title}")
            Text("A fatura integral continua sua obrigação. Confirme o saldo em aberto de cada responsável; pagamentos anteriores à conciliação não serão inventados.", style = MaterialTheme.typography.bodySmall)
            p.parts.forEachIndexed { i, part ->
                Text("Parcela ${part.number}/${p.parts.size} · ${ledgerMoney(part.cents)}")
                if (p.shares.keys.any { it > 0 }) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (part.number in p.familyOpening) "Gerar valor a receber" else "Histórico a conciliar", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(part.number in p.familyOpening, { open ->
                        if (!open) p.shares.keys.forEach { invalidInputs.remove("opening:$i:$it") }
                        draft = draft!!.copy(
                            familyOpening = if (open) draft!!.familyOpening + part.number else draft!!.familyOpening - part.number,
                            openingBalances = if (open) draft!!.openingBalances else draft!!.openingBalances.filterKeys { !it.startsWith("${part.number}:") },
                        )
                    })
                }
                Text("Vencimento · ${AppDates.date(part.due)}", style = MaterialTheme.typography.bodySmall)
                var totalText by remember(i) { mutableStateOf(editMoney(part.cents)) }
                LedgerField("Valor da parcela", totalText, monetary = true) { text ->
                    totalText = text
                    val cents = runCatching { FinanceLedger.money(text) }.getOrNull()
                    invalidInputs["total:$i"] = cents == null || cents <= 0
                    if (cents != null) draft = draft!!.copy(parts = draft!!.parts.mapIndexed { j, value -> if (j == i) value.copy(cents = cents) else value })
                }
                p.shares.forEach { (person, amounts) ->
                    if (person > 0 && part.number in p.familyOpening) {
                        val openingKey = "${part.number}:$person"
                        var opening by remember(i, person, "opening", amounts[i]) { mutableStateOf(editMoney(p.openingBalances[openingKey] ?: amounts[i])) }
                        LedgerField("${personLabel(state, person)} · saldo em aberto", opening, monetary = true) { text ->
                            opening = text
                            val cents = runCatching { FinanceLedger.money(text) }.getOrNull()
                            invalidInputs["opening:$i:$person"] = cents == null
                            if (cents != null) draft = draft!!.copy(openingBalances = draft!!.openingBalances + (openingKey to cents))
                        }
                    }
                    var amount by remember(i, person) { mutableStateOf(editMoney(amounts[i])) }
                    LedgerField(personLabel(state, person), amount, monetary = true) { text ->
                        amount = text
                        val parsedAmount = runCatching { FinanceLedger.money(text) }.getOrNull()
                        invalidInputs["share:$i:$person"] = parsedAmount == null
                        if (parsedAmount != null) invalidInputs.remove("opening:$i:$person")
                        parsedAmount?.let { cents ->
                            draft = draft!!.copy(shares = draft!!.shares.toMutableMap().apply { this[person] = getValue(person).mapIndexed { j, value -> if (j == i) cents else value } })
                        }
                    }
                }
            }
            TextButton(onClick = { draft = null }) { Text("Voltar à divisão") }
        }
        numbering.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (chooseLink && interactive) AppAlertDialog(onDismissRequest = ::resetPeople,
        title = { Text("Escolher parcela") }, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        containerColor = androidx.compose.ui.graphics.Color(0xFF0C162B), text = {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                linkOptions.forEach { (plan, part) ->
                    Surface(shape = FinanceInputShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .06f), onClick = {
                        chosenLink = plan to part
                        val participants = plan.shares.keys.toList()
                        val amounts = PurchaseSplit.proportionally(movement.row.cents, participants.map { plan.shares.getValue(it)[part.number - 1] })
                        division = PaymentDivision(movement.row.cents, participants, DivisionMode.AMOUNT, initial = amounts)
                        inputs.clear(); chooseLink = false; localError = null
                    }) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(plan.title, style = MaterialTheme.typography.titleSmall)
                            Text("${part.number}/${plan.parts.size} · ${ledgerMoney(part.cents)}", style = MaterialTheme.typography.bodyMedium)
                            Text(AppDates.date(part.due), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = ::resetPeople) { Text("Cancelar") } })
}

@Composable
internal fun PaymentDialog(personId: Long, direction: DebtDirection, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit, initialMovement: Long = 0) {
    var movementId by remember { mutableLongStateOf(initialMovement) }
    val allocations = remember { mutableStateMapOf<String, String>() }
    val debts = state.ledger.debts.filter { it.personId == personId && it.direction == direction && it.remaining > 0 }.sortedBy { it.due }
    val sources = state.movements.filter { it.row.kind == DocumentKind.NUBANK_ACCOUNT && it.id !in state.ledger.usedMovementIds && it.status != "CONFLICT" && !PaymentMatching.isPayment(it.row) && if (direction == DebtDirection.RECEIVE) it.row.cents > 0 else it.row.cents < 0 }
    val source = sources.firstOrNull { it.id == movementId }
    var localError by remember { mutableStateOf<String?>(null) }
    LedgerDialog(if (direction == DebtDirection.RECEIVE) "Registrar recebimento" else "Registrar pagamento", state, close, confirm = {
        try {
            require(source != null) { "Selecione a transferência." }
            val choices = allocations.filterValues { it.isNotBlank() }.mapValues { FinanceLedger.money(it.value) }.filterValues { it > 0 }
            require(choices.values.sum() == abs(source.row.cents)) { "Distribua exatamente ${ledgerMoney(abs(source.row.cents))}." }
            model.saveLedger(LedgerAction.Payment(personId, direction, source.id, source.row.date, choices), close)
        } catch (e: Exception) { localError = e.message ?: "Revise os valores." }
    }) {
        Text(personLabel(state, personId))
        LedgerChoice("Transferência", source?.let { "${AppDates.date(it.row.date)} · ${ledgerMoney(abs(it.row.cents))}" } ?: "Escolher", sources.map { m -> "${AppDates.date(m.row.date)} · ${ledgerMoney(abs(m.row.cents))} · ${m.friendlyName.ifBlank { m.row.description }}" to { movementId = m.id } })
        Text("Distribua o valor entre as dívidas. Receber e pagar são controles separados.", style = MaterialTheme.typography.bodySmall)
        if (debts.isEmpty()) Text("Nenhuma dívida em aberto nesta direção.")
        debts.forEach { d ->
            Text("Em aberto: ${ledgerMoney(d.remaining)}", style = MaterialTheme.typography.bodySmall)
            LedgerField(d.title, allocations[d.id].orEmpty(), monetary = true) { allocations[d.id] = it }
        }
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun ManualDebtDialog(personId: Long, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var title by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(LocalDate.now().withDayOfMonth(8).let { if (it < LocalDate.now()) it.plusMonths(1) else it }.let { BankCalendar.propose(it) }.let(AppDates::date)) }
    var direction by remember { mutableStateOf(DebtDirection.RECEIVE) }; var error by remember { mutableStateOf<String?>(null) }
    val id = remember { UUID.randomUUID().toString() }
    LedgerDialog("Nova dívida", state, close, confirm = {
        try { model.saveLedger(LedgerAction.Debt(id, personId, direction, title.trim(), FinanceLedger.money(amount), AppDates.parse(due)), close) }
        catch (_: Exception) { error = "Revise descrição, valor e vencimento." }
    }) {
        Text(personLabel(state, personId))
        LedgerChoice("Direção", if (direction == DebtDirection.RECEIVE) "A receber" else "Eu devo", DebtDirection.entries.map { d -> (if (d == DebtDirection.RECEIVE) "A receber" else "Eu devo") to { direction = d } })
        LedgerField("Descrição", title) { title = it }; LedgerField("Valor", amount, monetary = true) { amount = it }
        LedgerField("Vencimento", due) { due = it }
        Text("Confirme a data, inclusive ajustes por dia útil. Não há juros nem compensação automática.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun RefundDialog(movement: ReviewMovement, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var purchaseId by remember { mutableStateOf("") }
    val credits = remember { mutableStateMapOf<String, String>() }
    val selectedParts = remember { mutableStateListOf<Int>() }
    var error by remember { mutableStateOf<String?>(null) }
    val p = state.ledger.purchases.firstOrNull { it.id == purchaseId }
    LedgerDialog("Revisar estorno", state, close, confirm = {
        try {
            require(p != null) { "Escolha a compra original." }
            val amounts = credits.filterValues { it.isNotBlank() }.mapValues { FinanceLedger.money(it.value) }.filterValues { it > 0 }
            require(amounts.values.sum() == abs(movement.row.cents)) { "A distribuição precisa somar ${ledgerMoney(abs(movement.row.cents))}." }
            require(amounts.keys.all { key -> selectedParts.any { key.startsWith("${p.id}:$it:") } }) { "Selecione as parcelas afetadas." }
            model.saveLedger(LedgerAction.Refund(p.id, movement.id, movement.row.date, amounts), close)
        } catch (e: Exception) { error = e.message ?: "Revise os valores do estorno." }
    }) {
        Text("Crédito: ${ledgerMoney(abs(movement.row.cents))}")
        LedgerChoice("Compra original", p?.title ?: "Escolher", state.ledger.purchases.map { plan -> plan.title to { purchaseId = plan.id; credits.clear(); selectedParts.clear() } })
        Text("Selecione expressamente as parcelas afetadas. As próximas parcelas não são canceladas automaticamente.", style = MaterialTheme.typography.bodySmall)
        p?.parts?.forEach { part ->
            OutlinedButton(onClick = { if (part.number in selectedParts) { selectedParts.remove(part.number); p.shares.keys.forEach { credits.remove("${p.id}:${part.number}:$it") } } else selectedParts.add(part.number) }) {
                Text("${if (part.number in selectedParts) "✓ " else ""}${part.number}/${p.parts.size} · ${AppDates.date(part.due)}")
            }
            if (part.number in selectedParts) p.shares.forEach { (person, amounts) ->
                val id = "${p.id}:${part.number}:$person"
                val refunded = state.ledger.actions.filterIsInstance<LedgerAction.Refund>().sumOf { it.credits[id] ?: 0 }
                if (amounts[part.number - 1] > refunded) {
                    Text("Limite: ${ledgerMoney(amounts[part.number - 1] - refunded)}", style = MaterialTheme.typography.bodySmall)
                    LedgerField(personLabel(state, person), credits[id].orEmpty(), monetary = true) { credits[id] = it }
                }
            }
        }
        if (p != null && selectedParts.isNotEmpty()) OutlinedButton(onClick = {
            try {
                val suggestion = FinanceLedger.suggestRefund(state.ledger, p.id, selectedParts.toSet(), abs(movement.row.cents))
                credits.clear(); suggestion.forEach { (id, cents) -> credits[id] = editMoney(cents) }
                error = null
            } catch (e: Exception) { error = e.message ?: "Revise as parcelas selecionadas." }
        }) { Text("Sugerir divisão proporcional") }
        Text("Pagamentos anteriores são preservados. Se uma parte registrada já foi paga, o valor a devolver aparece separado em Pessoas. Partes históricas sem conciliação não presumem pagamentos: confirme eventuais valores a devolver por dívida identificada.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun MovementFinanceDialog(movement: ReviewMovement, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var step by remember { mutableStateOf("") }
    var person by remember { mutableLongStateOf(0) }
    var creditReason by remember { mutableStateOf("") }
    val row = movement.row
    when (step) {
        "purchase" -> PurchaseDialog(movement, state, model, close)
        "refund" -> RefundDialog(movement, state, model, close)
        "other-credit" -> LedgerDialog("Outro crédito da fatura", state, close, confirm = { model.saveLedger(LedgerAction.CardCredit(movement.id, creditReason.trim()), close) }) {
            Text("${row.description} · ${ledgerMoney(-row.cents)}")
            LedgerField("Motivo", creditReason) { creditReason = it }
            Text("Não será tratado como estorno de compra nem alterará dívidas de familiares.", style = MaterialTheme.typography.bodySmall)
        }
        "payment" -> PaymentDialog(person, if (row.cents > 0) DebtDirection.RECEIVE else DebtDirection.PAY, state, model, close, movement.id)
        else -> LedgerDialog("Detalhes da movimentação", state, close) {
            Text(movement.friendlyName.ifBlank { row.description })
            Text("${AppDates.date(row.date)} · ${ledgerMoney(row.cents)}")
            Text("Original: ${row.description}", style = MaterialTheme.typography.bodySmall)
            val plan = state.ledger.purchases.firstOrNull { p -> p.parts.any { it.movementId == movement.id } }
            when {
                plan != null -> {
                    Text("Compra vinculada · ${plan.parts.size} parcela(s)")
                    plan.shares.forEach { (id, values) -> Text("${personLabel(state, id)} · ${ledgerMoney(values.sum())}") }
                    Text("As parcelas seguintes devem ser vinculadas ao importar a próxima fatura.", style = MaterialTheme.typography.bodySmall)
                }
                movement.id in state.ledger.usedMovementIds -> Text("Movimentação já vinculada ao histórico financeiro.")
                movement.status == "CONFLICT" -> {
                    Text("Esta versão diverge do histórico. Desconsiderar não apaga o arquivo nem o registro original. Manter como distinta acrescenta uma operação separada; confirme apenas se ela realmente ocorreu.")
                    OutlinedButton(onClick = { model.saveLedger(LedgerAction.ResolveConflict(movement.id, false), close) }, enabled = !state.syncing) { Text("Desconsiderar esta versão") }
                    OutlinedButton(onClick = { model.saveLedger(LedgerAction.ResolveConflict(movement.id, true), close) }, enabled = !state.syncing) { Text("Manter como operação distinta") }
                }
                movement.status == "IGNORED" -> Text("Versão desconsiderada nos cálculos; registro original preservado para auditoria.")
                row.kind == DocumentKind.NUBANK_CARD && row.cents > 0 -> Button(onClick = { step = "purchase" }) { Text("Responsáveis e parcelas") }
                row.kind == DocumentKind.NUBANK_CARD && row.cents < 0 && !PaymentMatching.isPayment(row) -> {
                    Button(onClick = { step = "refund" }) { Text("Vincular estorno") }
                    OutlinedButton(onClick = { step = "other-credit" }) { Text("Outro crédito da fatura") }
                }
                row.kind == DocumentKind.NUBANK_ACCOUNT && !PaymentMatching.isPayment(row) -> {
                    val identity = CounterpartySuggestion.identify(row)
                    val suggested = state.people.firstOrNull { br.com.yurimedeiros.meufinanceiro.people.PersonNames.key(it.name) == br.com.yurimedeiros.meufinanceiro.people.PersonNames.key(identity.displayName) }
                    suggested?.let { TextButton(onClick = { person = it.id }) { Text("Possível responsável: ${it.name}") } }
                    LedgerChoice("Responsável", if (person == 0L) "Escolher" else personLabel(state, person), state.people.map { p -> p.name to { person = p.id } })
                    Button(onClick = { step = "payment" }, enabled = person > 0) { Text(if (row.cents > 0) "Abater dívidas a receber" else "Abater valores que devo") }
                }
            }
            if (movement.status == "PAYMENT_PENDING") {
                Text("Escolha a contrapartida de mesma quantia. Datas diferentes exigem esta confirmação manual.", style = MaterialTheme.typography.bodySmall)
                val candidates = state.movements.filter { it.id != movement.id && it.status == "PAYMENT_PENDING" && it.row.kind != row.kind && it.row.cents == row.cents && PaymentMatching.isPayment(it.row) }
                if (candidates.isEmpty()) Text("Sem contrapartida disponível no extrato.")
                candidates.forEach { other -> Button(onClick = {
                    val account = if (row.kind == DocumentKind.NUBANK_ACCOUNT) movement else other
                    val card = if (row.kind == DocumentKind.NUBANK_CARD) movement else other
                    card.row.statement?.let { model.saveLedger(LedgerAction.InvoicePaid(it, account.id, card.id), close) }
                }, enabled = !state.syncing) { Text("Conciliar com ${AppDates.date(other.row.date)} · ${ledgerMoney(abs(other.row.cents))}") } }
            }
        }
    }
}
