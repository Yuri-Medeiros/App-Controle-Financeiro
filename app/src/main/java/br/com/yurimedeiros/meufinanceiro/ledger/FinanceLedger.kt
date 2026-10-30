package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.planning.*
import br.com.yurimedeiros.meufinanceiro.assets.*
import java.math.BigDecimal
import java.time.LocalDate

internal enum class DebtDirection { RECEIVE, PAY }
internal data class SchedulePart(val number: Int, val due: LocalDate, val cents: Long, val movementId: Long = 0)
internal data class PurchasePlan(
    val id: String, val title: String, val category: String,
    val parts: List<SchedulePart>, val shares: Map<Long, List<Long>>,
    val familyOpening: Set<Int> = parts.map { it.number }.toSet(), val familyStart: LocalDate? = null, val openingBalances: Map<String, Long> = emptyMap(),
)
internal sealed interface LedgerAction {
    data class CardCredit(val movementId: Long, val reason: String) : LedgerAction
    data class FlashCredit(val movementId: Long, val sunday: LocalDate) : LedgerAction
    data class Asset(val change: AssetChange) : LedgerAction
    data class Planning(val settings: PlanningState) : LedgerAction
    data class ForecastMatch(val expenseId: String, val nominal: LocalDate, val movementId: Long) : LedgerAction
    data class ResolveConflict(val movementId: Long, val keepDistinct: Boolean) : LedgerAction
    data class Purchase(val plan: PurchasePlan) : LedgerAction
    data class Link(val purchaseId: String, val number: Int, val movementId: Long) : LedgerAction
    data class AdjustLink(val purchaseId: String, val number: Int, val movementId: Long, val cents: Long, val due: LocalDate, val shares: Map<Long, Long>) : LedgerAction
    data class Debt(val id: String, val personId: Long, val direction: DebtDirection, val title: String, val cents: Long, val due: LocalDate) : LedgerAction
    data class Payment(val personId: Long, val direction: DebtDirection, val movementId: Long, val date: LocalDate, val allocations: Map<String, Long>) : LedgerAction
    data class Refund(val purchaseId: String, val movementId: Long, val date: LocalDate, val credits: Map<String, Long>) : LedgerAction
    data class Postpone(val debtId: String, val due: LocalDate, val reason: String) : LedgerAction
    data class InvoicePaid(val reference: LocalDate, val accountMovementId: Long, val cardMovementId: Long) : LedgerAction
}
internal data class DebtBalance(
    val id: String, val personId: Long, val direction: DebtDirection, val title: String,
    val due: LocalDate, val originalCents: Long, val creditedCents: Long = 0, val paidCents: Long = 0,
    val purchaseId: String? = null, val number: Int? = null,
) {
    val remaining: Long get() = (originalCents - creditedCents - paidCents).coerceAtLeast(0)
    val overpaid: Long get() = (paidCents - (originalCents - creditedCents)).coerceAtLeast(0)
}
internal data class LedgerSnapshot(
    val purchases: List<PurchasePlan> = emptyList(), val debts: List<DebtBalance> = emptyList(),
    val actions: List<LedgerAction> = emptyList(), val planning: PlanningState = PlanningState(), val assets: AssetBook = AssetBook(),
) {
    val usedMovementIds: Set<Long> get() = buildSet {
        purchases.forEach { p -> p.parts.forEach { if (it.movementId > 0) add(it.movementId) } }
        actions.forEach { a -> when (a) {
            is LedgerAction.Asset -> (a.change as? AssetChange.Transfer)?.let { add(it.movementId) }
            is LedgerAction.CardCredit -> add(a.movementId)
            is LedgerAction.FlashCredit -> add(a.movementId)
            is LedgerAction.ForecastMatch -> add(a.movementId)
            is LedgerAction.Payment -> add(a.movementId)
            is LedgerAction.Refund -> add(a.movementId)
            is LedgerAction.InvoicePaid -> { add(a.accountMovementId); add(a.cardMovementId) }
            else -> Unit
        } }
    }
}

/** Exact cents only. Append-only actions retain allocations, refunds and due-date history. */
internal object FinanceLedger {
    fun suggestRefund(s: LedgerSnapshot, purchaseId: String, numbers: Set<Int>, cents: Long): Map<String, Long> {
        require(cents > 0 && numbers.isNotEmpty())
        val p = s.purchases.single { it.id == purchaseId }
        require(numbers.all { number -> p.parts.any { it.number == number } })
        val shares = p.parts.filter { it.number in numbers }.flatMap { part -> p.shares.map { (person, amounts) ->
            val id = "${p.id}:${part.number}:$person"
            val prior = s.actions.filterIsInstance<LedgerAction.Refund>().sumOf { it.credits[id] ?: 0 }
            id to (amounts[part.number - 1] - prior)
        } }.filter { it.second > 0 }
        require(shares.isNotEmpty() && cents <= shares.map { it.second }.fold(0L, Math::addExact)) { "O estorno excede as partes selecionadas." }
        val amounts = br.com.yurimedeiros.meufinanceiro.domain.PurchaseSplit.proportionally(cents, shares.map { it.second })
        return shares.mapIndexed { i, share -> share.first to amounts[i] }.filter { it.second > 0 }.toMap()
    }

    fun money(text: String): Long {
        val cleaned = text.trim().replace("R$", "").replace(" ", "")
        val decimal = if (',' in cleaned) cleaned.replace(".", "").replace(',', '.') else cleaned
        return BigDecimal(decimal).movePointRight(2).longValueExact().also { require(it >= 0) { "Informe um valor positivo." } }
    }
    fun replay(actions: List<LedgerAction>): LedgerSnapshot {
        var result = LedgerSnapshot()
        actions.forEach { result = apply(result, it) }
        return result
    }
    fun apply(s: LedgerSnapshot, a: LedgerAction): LedgerSnapshot {
        var purchases = s.purchases
        var debts = s.debts
        var planning = s.planning
        var assets = s.assets
        when (a) {
            is LedgerAction.CardCredit -> require(a.movementId > 0 && a.movementId !in s.usedMovementIds && a.reason.isNotBlank() && a.reason.length <= 240)
            is LedgerAction.FlashCredit -> {
                require(a.movementId > 0 && a.movementId !in s.usedMovementIds && a.sunday.dayOfWeek == java.time.DayOfWeek.SUNDAY)
                require(s.actions.filterIsInstance<LedgerAction.FlashCredit>().none { it.sunday == a.sunday })
            }
            is LedgerAction.Asset -> {
                (a.change as? AssetChange.Transfer)?.let { require(it.movementId !in s.usedMovementIds) }
                assets = AssetRules.apply(assets, a.change)
            }
            is LedgerAction.Planning -> { PlanningRules.validate(a.settings, planning); planning = a.settings }
            is LedgerAction.ForecastMatch -> {
                require(a.movementId > 0 && a.movementId !in s.usedMovementIds)
                val e = planning.expenses.single { it.id == a.expenseId }
                require(PlanningRules.occurrences(e, a.nominal).any { it.nominal == a.nominal })
                require(s.actions.filterIsInstance<LedgerAction.ForecastMatch>().none { it.expenseId == a.expenseId && it.nominal == a.nominal })
            }
            is LedgerAction.ResolveConflict -> require(a.movementId > 0 && a.movementId !in s.usedMovementIds)
            is LedgerAction.Purchase -> {
                val p = a.plan
                require(p.id.isNotBlank() && purchases.none { it.id == p.id }) { "Compra já registrada." }
                require(p.title.isNotBlank() && p.category.isNotBlank()) { "Informe descrição e categoria." }
                require(p.parts.isNotEmpty() && p.parts.size <= 120)
                require(p.parts.map { it.number } == (1..p.parts.size).toList())
                require(p.shares.isNotEmpty() && p.shares.keys.all { it >= 0 })
                require(p.familyOpening.all { number -> p.parts.any { it.number == number } })
                p.openingBalances.forEach { (key, cents) ->
                    val ids = key.split(':'); require(ids.size == 2)
                    val number = ids[0].toInt(); val person = ids[1].toLong()
                    require(number in p.familyOpening && person > 0 && cents in 0..p.shares.getValue(person)[number - 1])
                }
                require(p.shares.values.all { it.size == p.parts.size && it.all { c -> c >= 0 } })
                p.parts.forEachIndexed { i, part ->
                    require(part.cents > 0 && p.shares.values.map { it[i] }.fold(0L, Math::addExact) == part.cents) { "A divisão precisa fechar o valor de cada parcela." }
                    require(part.movementId == 0L || part.movementId !in s.usedMovementIds) { "Movimentação já vinculada." }
                }
                val linked = p.parts.filter { it.movementId != 0L }.map { it.movementId }
                require(linked.distinct().size == linked.size)
                purchases = purchases + p
                debts = debts + p.shares.flatMap { (person, amounts) ->
                    amounts.mapIndexedNotNull { i, assigned ->
                        val cents = p.openingBalances["${i + 1}:$person"] ?: assigned
                        if (person == 0L || cents == 0L || i + 1 !in p.familyOpening) null else DebtBalance(
                        "${p.id}:${i + 1}:$person", person, DebtDirection.RECEIVE,
                        "${p.title} · ${i + 1}/${p.parts.size}", p.parts[i].due, cents,
                        purchaseId = p.id, number = i + 1,
                    ) }
                }
            }
            is LedgerAction.Link -> {
                require(a.movementId > 0 && a.movementId !in s.usedMovementIds) { "Movimentação já vinculada." }
                val p = purchases.single { it.id == a.purchaseId }
                require(p.parts.any { it.number == a.number && it.movementId == 0L }) { "Parcela já vinculada." }
                purchases = purchases.map { if (it.id != p.id) it else it.copy(parts = it.parts.map { part -> if (part.number != a.number) part else part.copy(movementId = a.movementId) }) }
            }
            is LedgerAction.AdjustLink -> {
                require(a.movementId > 0 && a.movementId !in s.usedMovementIds)
                val p = purchases.single { it.id == a.purchaseId }
                val part = p.parts.single { it.number == a.number }
                require(part.movementId == 0L) { "Parcela já vinculada." }
                require(a.cents > 0 && a.shares.keys == p.shares.keys && a.shares.values.all { it >= 0 })
                require(a.shares.values.fold(0L, Math::addExact) == a.cents) { "A divisão precisa fechar a parcela importada." }
                require(debts.filter { it.purchaseId == p.id && it.number == a.number }.all { it.paidCents == 0L && it.creditedCents == 0L }) { "Parcela com pagamentos ou estornos: preserve os valores e revise o vínculo." }
                purchases = purchases.map { if (it.id != p.id) it else it.copy(
                    parts = it.parts.map { old -> if (old.number != a.number) old else old.copy(cents = a.cents, due = a.due, movementId = a.movementId) },
                    shares = it.shares.mapValues { (person, amounts) -> amounts.mapIndexed { i, value -> if (i + 1 == a.number) a.shares.getValue(person) else value } },
                    openingBalances = it.openingBalances.mapValues { (key, balance) ->
                        val ids = key.split(':'); val number = ids[0].toInt(); val person = ids[1].toLong()
                        if (number == a.number && balance == it.shares.getValue(person)[number - 1]) a.shares.getValue(person) else balance
                    },
                ) }
                debts = debts.filterNot { it.purchaseId == p.id && it.number == a.number } + p.shares.keys.mapNotNull { person ->
                    val oldOpening = p.openingBalances["${a.number}:$person"]
                    val cents = if (oldOpening == null || oldOpening == p.shares.getValue(person)[a.number - 1]) a.shares.getValue(person) else oldOpening
                    require(cents <= a.shares.getValue(person)) { "Revise o saldo inicial desta parcela antes de ajustar a divisão." }
                    if (person == 0L || cents == 0L || a.number !in p.familyOpening) null else DebtBalance("${p.id}:${a.number}:$person", person, DebtDirection.RECEIVE, "${p.title} · ${a.number}/${p.parts.size}", a.due, cents, purchaseId = p.id, number = a.number)
                }
            }
            is LedgerAction.Debt -> {
                require(a.personId > 0 && a.cents > 0 && a.title.isNotBlank())
                require(debts.none { it.id == a.id })
                debts = debts + DebtBalance(a.id, a.personId, a.direction, a.title, a.due, a.cents)
            }
            is LedgerAction.Payment -> {
                require(a.movementId > 0 && a.movementId !in s.usedMovementIds) { "Este pagamento já foi utilizado." }
                require(a.allocations.isNotEmpty() && a.allocations.values.all { it > 0 })
                a.allocations.forEach { (id, cents) ->
                    val d = debts.single { it.id == id }
                    require(d.personId == a.personId && d.direction == a.direction && cents <= d.remaining) { "Distribuição excede a dívida ou pertence a outra pessoa." }
                }
                debts = debts.map { d -> d.copy(paidCents = Math.addExact(d.paidCents, a.allocations[d.id] ?: 0)) }
            }
            is LedgerAction.Refund -> {
                require(a.movementId > 0 && a.movementId !in s.usedMovementIds) { "Estorno já utilizado." }
                val p = purchases.single { it.id == a.purchaseId }
                require(a.credits.isNotEmpty() && a.credits.values.all { it > 0 })
                a.credits.forEach { (id, cents) ->
                    // Own shares use the same identity scheme but never create family debt.
                    val key = id.removePrefix("${p.id}:").split(':')
                    require(id.startsWith("${p.id}:") && key.size == 2)
                    val number = key[0].toInt(); val person = key[1].toLong()
                    val original = p.shares.getValue(person)[number - 1]
                    val previous = s.actions.filterIsInstance<LedgerAction.Refund>().sumOf { it.credits[id] ?: 0 }
                    require(cents <= original - previous) { "Estorno excede a parte original." }
                }
                debts = debts.map { d ->
                    // Historical opening balances do not prove earlier receipts. Only
                    // recorded payments can create an automatic refund payable.
                    val credit = (a.credits[d.id] ?: 0).coerceAtMost(d.originalCents - d.creditedCents)
                    d.copy(creditedCents = Math.addExact(d.creditedCents, credit))
                }
                // A refund of an already-paid share becomes a separate amount owed back.
                // Earlier payments and unrelated debts are never silently compensated.
                debts = debts + debts.filter { it.purchaseId == p.id }.mapNotNull { d ->
                    val old = s.debts.single { it.id == d.id }
                    val increase = d.overpaid - old.overpaid
                    if (increase <= 0) null else DebtBalance("return:${a.movementId}:${d.id}", d.personId,
                        DebtDirection.PAY, "Devolver estorno · ${p.title}", a.date, increase)
                }
            }
            is LedgerAction.Postpone -> {
                val d = debts.single { it.id == a.debtId }
                require(d.purchaseId == null && d.remaining > 0 && a.reason.isNotBlank()) { "Só dívidas fora do cartão podem ser prorrogadas; informe o motivo." }
                debts = debts.map { if (it.id == d.id) it.copy(due = a.due) else it }
            }
            is LedgerAction.InvoicePaid -> {
                require(a.accountMovementId > 0 && a.cardMovementId > 0 && a.accountMovementId != a.cardMovementId)
                require(a.accountMovementId !in s.usedMovementIds && a.cardMovementId !in s.usedMovementIds)
                // Bank settlement never changes personal receivables.
            }
        }
        return LedgerSnapshot(purchases, debts, s.actions + a, planning, assets)
    }
}
