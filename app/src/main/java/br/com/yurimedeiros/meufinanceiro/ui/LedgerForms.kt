package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
internal fun LedgerField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = FinanceInputShape, singleLine = true)
}

@Composable
internal fun LedgerChoice(label: String, value: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = FinanceInputShape) { Text("$label: $value") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
    AlertDialog(
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
    val parsed = remember(movement.id) { Regex("(\\d{1,3})\\s*/\\s*(\\d{1,3})").find(movement.row.description) }
    var mode by remember { mutableIntStateOf(0) }
    var count by remember { mutableStateOf(parsed?.groupValues?.get(2) ?: "1") }
    var current by remember { mutableStateOf(parsed?.groupValues?.get(1) ?: "1") }
    var title by remember { mutableStateOf(movement.friendlyName.ifBlank { movement.row.description }) }
    var category by remember { mutableStateOf(movement.category) }
    var firstDue by remember { mutableStateOf((movement.row.statement ?: movement.row.date).minusMonths((current.toLongOrNull() ?: 1) - 1).toString()) }
    var familyStart by remember { mutableStateOf(state.ledger.purchases.lastOrNull { it.familyStart != null }?.familyStart?.toString().orEmpty()) }
    var participants by remember { mutableStateOf(listOf(0L)) }
    var split by remember { mutableIntStateOf(0) }
    val values = remember { mutableStateMapOf<Long, String>() }
    var futureDatesConfirmed by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<PurchasePlan?>(null) }
    var chosenLink by remember { mutableStateOf<Pair<PurchasePlan, SchedulePart>?>(null) }
    val linkAmounts = remember { mutableStateMapOf<Long, String>() }
    var linkDue by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val id = remember { UUID.randomUUID().toString() }
    val invalidInputs = remember { mutableStateMapOf<String, Boolean>() }
    fun prepare() {
        try {
            val n = count.toInt(); val number = current.toInt()
            require(n in 1..120 && number in 1..n) { "Revise o número da parcela e a quantidade (até 120)." }
            require(category.isNotBlank() && title.isNotBlank()) { "Informe categoria e descrição." }
            val date = LocalDate.parse(firstDue)
            val cutoff = if (participants.any { it > 0 }) LocalDate.parse(familyStart) else null
            val shares = when (split) {
                0 -> PurchaseSplit.equally(movement.row.cents, participants.size)
                1 -> PurchaseSplit.byPercentages(movement.row.cents, participants.map { BigDecimal(values[it].orEmpty().replace(',', '.')) })
                else -> participants.map { FinanceLedger.money(values[it].orEmpty()) }.also { require(it.sum() == movement.row.cents) { "Os valores precisam somar a parcela importada." } }
            }
            draft = PurchasePlan(id, title.trim(), category.trim(), (1..n).map {
                SchedulePart(it, BankCalendar.propose(date.plusMonths(it - 1L)), movement.row.cents, if (it == number) movement.id else 0)
            }, participants.mapIndexed { i, person -> person to List(n) { shares[i] } }.toMap(),
                if (cutoff == null) emptySet() else (1..n).filter { date.plusMonths(it - 1L) >= cutoff }.toSet(), cutoff)
            invalidInputs.clear()
            futureDatesConfirmed = false
            localError = null
        } catch (_: Exception) { localError = "Revise quantidade, datas, categoria e divisão. Percentuais somam 100; valores somam a parcela." }
    }
    LedgerDialog("Responsáveis e parcelas", state, close,
        confirmLabel = if (draft == null) "Revisar valores" else "Salvar compra",
        confirm = if (mode == 0) ({ if (draft == null) prepare() else {
            try { require(invalidInputs.values.none { it }) { "Corrija os campos inválidos da prévia." }; require(futureDatesConfirmed || draft!!.parts.all { BankCalendar.adjust(it.due) != null }) { "Confirme as datas dos anos ainda não validados." }; require(draft!!.parts.all { it.due.dayOfWeek !in setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY) && BankCalendar.adjust(it.due)?.let { adjusted -> adjusted == it.due } != false }) { "Revise vencimentos em fins de semana ou feriados conhecidos." }; FinanceLedger.apply(state.ledger, LedgerAction.Purchase(draft!!)); model.saveLedger(LedgerAction.Purchase(draft!!), close) }
            catch (e: Exception) { localError = e.message ?: "Revise a divisão de cada parcela." }
        } }) else null,
    ) {
        if (draft == null) {
            PillTabs(listOf("Nova compra", "Vincular parcela"), mode) { mode = it }
            if (mode == 1) {
                Text("Selecione a parcela já prevista. Nada será recriado.", style = MaterialTheme.typography.bodySmall)
                val options = state.ledger.purchases.flatMap { p -> p.parts.filter { it.movementId == 0L }.map { p to it } }
                if (options.isEmpty()) Text("Nenhuma parcela prevista para vincular.")
                options.forEach { (p, part) -> OutlinedButton(onClick = {
                    chosenLink = p to part; linkDue = part.due.toString(); linkAmounts.clear()
                    val people = p.shares.keys.toList()
                    val suggested = PurchaseSplit.proportionally(movement.row.cents, people.map { p.shares.getValue(it)[part.number - 1] })
                    people.forEachIndexed { i, person -> linkAmounts[person] = editMoney(suggested[i]) }
                }, enabled = !state.syncing) {
                    Text("${p.title} · ${part.number}/${p.parts.size} · previsto ${ledgerMoney(part.cents)}")
                } }
                chosenLink?.let { (p, part) ->
                    Text("Importado ${ledgerMoney(movement.row.cents)} · confirme diferenças e divisão.")
                    LedgerField("Vencimento", linkDue) { linkDue = it }
                    p.shares.keys.forEach { person -> LedgerField(personLabel(state, person), linkAmounts[person].orEmpty()) { linkAmounts[person] = it } }
                    Button(onClick = {
                        try {
                            val amounts = p.shares.keys.associateWith { FinanceLedger.money(linkAmounts[it].orEmpty()) }
                            val due = LocalDate.parse(linkDue)
                            val unchanged = movement.row.cents == part.cents && due == part.due && amounts.all { (person, cents) -> p.shares.getValue(person)[part.number - 1] == cents }
                            val action = if (unchanged) LedgerAction.Link(p.id, part.number, movement.id) else LedgerAction.AdjustLink(p.id, part.number, movement.id, movement.row.cents, due, amounts)
                            model.saveLedger(action, close)
                        } catch (_: Exception) { localError = "Revise os valores e o vencimento da parcela." }
                    }, enabled = !state.syncing) { Text("Confirmar vínculo") }
                }
            } else {
                if (state.ledger.purchases.any { p -> p.parts.any { it.movementId == 0L && it.cents == movement.row.cents } }) Text("Há parcelas previstas com este valor. Confira Vincular parcela antes de criar outra compra.", style = MaterialTheme.typography.bodySmall)
                LedgerField("Descrição", title) { title = it }
                CategoryDropdown(category, { category = it }, !state.syncing)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(current, { current = it }, label = { Text("Parcela atual") }, modifier = Modifier.weight(1f), shape = FinanceInputShape)
                    OutlinedTextField(count, { count = it }, label = { Text("Total") }, modifier = Modifier.weight(1f), shape = FinanceInputShape)
                }
                LedgerField("Primeiro vencimento nominal AAAA-MM-DD", firstDue) { firstDue = it }
                Text("Dias úteis são ajustados pelo calendário validado. Confirme os vencimentos na prévia, especialmente os anos ainda não validados.", style = MaterialTheme.typography.bodySmall)
                LedgerChoice("Adicionar responsável", "Escolher", (listOf(0L) + state.people.map { it.id }).filter { it !in participants }.map { person ->
                    personLabel(state, person) to { participants = participants + person }
                })
                participants.forEach { person -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(personLabel(state, person)); TextButton(onClick = { if (participants.size > 1) participants = participants - person }) { Text("Remover") }
                } }
                if (participants.any { it > 0 }) {
                    LedgerField("Conciliar familiares desde AAAA-MM-DD", familyStart) { familyStart = it }
                    Text("Compras anteriores ficam como histórico a conciliar. A data não presume recebimentos nem quitação; revise as parcelas em aberto na prévia.", style = MaterialTheme.typography.bodySmall)
                }
                LedgerChoice("Divisão", listOf("Igual", "Percentuais", "Valores por parcela")[split], listOf("Igual", "Percentuais", "Valores por parcela").mapIndexed { i, label -> label to { split = i } })
                if (split != 0) participants.forEach { person -> LedgerField("${personLabel(state, person)} ${if (split == 1) "%" else "R$"}", values[person].orEmpty()) { values[person] = it } }
                Text("Parcela importada: ${ledgerMoney(movement.row.cents)}", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            val p = draft!!
            Text("Prévia · ${p.title}")
            Text("A fatura integral continua sua obrigação. Confirme o saldo em aberto de cada responsável; pagamentos anteriores à conciliação não serão inventados.", style = MaterialTheme.typography.bodySmall)
            val unknownYears = p.parts.filter { BankCalendar.adjust(it.due) == null }.map { it.due.year }.distinct()
            if (unknownYears.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Confirmo os vencimentos de ${unknownYears.joinToString()}; calendário ainda não validado.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Checkbox(futureDatesConfirmed, { futureDatesConfirmed = it })
            }
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
                var dueText by remember(i) { mutableStateOf(part.due.toString()) }
                LedgerField("Vencimento", dueText) { text ->
                    dueText = text
                    val date = runCatching { LocalDate.parse(text) }.getOrNull()
                    invalidInputs["due:$i"] = date == null
                    if (date != null) draft = draft!!.copy(parts = draft!!.parts.mapIndexed { j, value -> if (j == i) value.copy(due = date) else value })
                }
                var totalText by remember(i) { mutableStateOf(editMoney(part.cents)) }
                LedgerField("Valor da parcela R$", totalText) { text ->
                    totalText = text
                    val cents = runCatching { FinanceLedger.money(text) }.getOrNull()
                    invalidInputs["total:$i"] = cents == null || cents <= 0
                    if (cents != null) draft = draft!!.copy(parts = draft!!.parts.mapIndexed { j, value -> if (j == i) value.copy(cents = cents) else value })
                }
                p.shares.forEach { (person, amounts) ->
                    if (person > 0 && part.number in p.familyOpening) {
                        val openingKey = "${part.number}:$person"
                        var opening by remember(i, person, "opening", amounts[i]) { mutableStateOf(editMoney(p.openingBalances[openingKey] ?: amounts[i])) }
                        LedgerField("${personLabel(state, person)} · saldo em aberto confirmado R$", opening) { text ->
                            opening = text
                            val cents = runCatching { FinanceLedger.money(text) }.getOrNull()
                            invalidInputs["opening:$i:$person"] = cents == null
                            if (cents != null) draft = draft!!.copy(openingBalances = draft!!.openingBalances + (openingKey to cents))
                        }
                    }
                    var amount by remember(i, person) { mutableStateOf(editMoney(amounts[i])) }
                    LedgerField(personLabel(state, person), amount) { text ->
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
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
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
        LedgerChoice("Transferência", source?.let { "${it.row.date} · ${ledgerMoney(abs(it.row.cents))}" } ?: "Escolher", sources.map { m -> "${m.row.date} · ${ledgerMoney(abs(m.row.cents))} · ${m.friendlyName.ifBlank { m.row.description }}" to { movementId = m.id } })
        Text("Distribua o valor entre as dívidas. Receber e pagar são controles separados.", style = MaterialTheme.typography.bodySmall)
        if (debts.isEmpty()) Text("Nenhuma dívida em aberto nesta direção.")
        debts.forEach { d -> LedgerField("${d.title} · aberto ${ledgerMoney(d.remaining)}", allocations[d.id].orEmpty()) { allocations[d.id] = it } }
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun ManualDebtDialog(personId: Long, state: FinanceUiState, model: FinanceViewModel, close: () -> Unit) {
    var title by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(LocalDate.now().withDayOfMonth(8).let { if (it < LocalDate.now()) it.plusMonths(1) else it }.let { BankCalendar.propose(it) }.toString()) }
    var direction by remember { mutableStateOf(DebtDirection.RECEIVE) }; var error by remember { mutableStateOf<String?>(null) }
    val id = remember { UUID.randomUUID().toString() }
    LedgerDialog("Nova dívida", state, close, confirm = {
        try { model.saveLedger(LedgerAction.Debt(id, personId, direction, title.trim(), FinanceLedger.money(amount), LocalDate.parse(due)), close) }
        catch (_: Exception) { error = "Revise descrição, valor e vencimento." }
    }) {
        Text(personLabel(state, personId))
        LedgerChoice("Direção", if (direction == DebtDirection.RECEIVE) "A receber" else "Eu devo", DebtDirection.entries.map { d -> (if (d == DebtDirection.RECEIVE) "A receber" else "Eu devo") to { direction = d } })
        LedgerField("Origem / descrição", title) { title = it }; LedgerField("Valor R$", amount) { amount = it }
        LedgerField("Vencimento AAAA-MM-DD", due) { due = it }
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
                Text("${if (part.number in selectedParts) "✓ " else ""}${part.number}/${p.parts.size} · ${part.due}")
            }
            if (part.number in selectedParts) p.shares.forEach { (person, amounts) ->
                val id = "${p.id}:${part.number}:$person"
                val refunded = state.ledger.actions.filterIsInstance<LedgerAction.Refund>().sumOf { it.credits[id] ?: 0 }
                if (amounts[part.number - 1] > refunded) LedgerField("${personLabel(state, person)} · limite ${ledgerMoney(amounts[part.number - 1] - refunded)}", credits[id].orEmpty()) { credits[id] = it }
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
            LedgerField("Natureza / motivo", creditReason) { creditReason = it }
            Text("Não será tratado como estorno de compra nem alterará dívidas de familiares.", style = MaterialTheme.typography.bodySmall)
        }
        "payment" -> PaymentDialog(person, if (row.cents > 0) DebtDirection.RECEIVE else DebtDirection.PAY, state, model, close, movement.id)
        else -> LedgerDialog("Detalhes da movimentação", state, close) {
            Text(movement.friendlyName.ifBlank { row.description })
            Text("${row.date} · ${ledgerMoney(row.cents)}")
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
                }, enabled = !state.syncing) { Text("Conciliar com ${other.row.date} · ${ledgerMoney(abs(other.row.cents))}") } }
            }
        }
    }
}
