package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.data.FinanceDatabase
import br.com.yurimedeiros.meufinanceiro.data.FinancialEvent
import br.com.yurimedeiros.meufinanceiro.importing.*
import br.com.yurimedeiros.meufinanceiro.assets.*
import kotlin.math.abs

internal class LedgerRepository(private val db: FinanceDatabase) {
    fun snapshot(): LedgerSnapshot = FinanceLedger.replay(db.financialEvents().all().map { LedgerCodec.decode(it.payload) })

    fun append(action: LedgerAction) = db.runInTransaction {
        val snapshot = snapshot()
        val people = db.people().all().map { it.id }.toSet()
        val movements = MovementRepository(db).all().associateBy { it.id }
        fun movement(id: Long): ReviewMovement = checkNotNull(movements[id]) { "Movimentação não encontrada." }.also {
            require(it.status !in setOf("CONFLICT", "IGNORED")) { "Resolva a divergência de importação primeiro." }
        }
        fun person(id: Long) { require(id == 0L || id in people) { "Responsável não encontrado." } }
        fun purchaseSource(id: Long, cents: Long) {
            val m = movement(id)
            require(m.row.kind == DocumentKind.NUBANK_CARD && m.row.cents == cents && cents > 0) { "Valor ou origem não corresponde à parcela." }
        }
        when (action) {
            is LedgerAction.CardCredit -> { val m = movement(action.movementId); require(m.row.kind == DocumentKind.NUBANK_CARD && m.row.cents < 0 && !PaymentMatching.isPayment(m.row)) }
            is LedgerAction.FlashCredit -> { val m = movement(action.movementId); require(m.row.kind == DocumentKind.FLASH && m.row.cents == 15000L && m.row.date == action.sunday) }
            is LedgerAction.Asset -> (action.change as? AssetChange.Transfer)?.let { transfer ->
                val m = movement(transfer.movementId)
                require(m.row.kind == DocumentKind.NUBANK_ACCOUNT && !PaymentMatching.isPayment(m.row))
                require(if (transfer.kind == AssetTransferKind.CONTRIBUTION) m.row.cents < 0 else m.row.cents > 0)
                require(abs(m.row.cents) == transfer.bankCents && m.row.date == transfer.date)
            }
            is LedgerAction.Planning -> Unit
            is LedgerAction.ResolveConflict -> require(movements.getValue(action.movementId).status == "CONFLICT")
            is LedgerAction.ForecastMatch -> {
                val m = movement(action.movementId)
                require(m.row.kind == DocumentKind.NUBANK_ACCOUNT && m.row.cents < 0 && !PaymentMatching.isPayment(m.row))
            }
            is LedgerAction.Purchase -> {
                action.plan.shares.keys.forEach(::person)
                require(action.plan.shares.keys.none { it > 0 } || action.plan.familyStart != null) { "Confirme a data inicial da conciliação familiar." }
                action.plan.parts.filter { it.movementId > 0 }.forEach { purchaseSource(it.movementId, it.cents) }
                require(action.plan.parts.any { it.movementId > 0 }) { "Vincule a compra ao extrato para não duplicar valores." }
            }
            is LedgerAction.Link -> purchaseSource(action.movementId, snapshot.purchases.single { it.id == action.purchaseId }.parts.single { it.number == action.number }.cents)
            is LedgerAction.AdjustLink -> purchaseSource(action.movementId, action.cents)
            is LedgerAction.Debt -> { require(action.personId > 0); person(action.personId) }
            is LedgerAction.Payment -> {
                person(action.personId)
                val m = movement(action.movementId)
                require(m.row.kind == DocumentKind.NUBANK_ACCOUNT && !PaymentMatching.isPayment(m.row)) { "Selecione uma transferência da conta, sem usar pagamento de fatura." }
                require(if (action.direction == DebtDirection.RECEIVE) m.row.cents > 0 else m.row.cents < 0)
                require(action.date == m.row.date)
                require(action.allocations.values.fold(0L, Math::addExact) == abs(m.row.cents)) { "Distribua o valor integral do pagamento entre as dívidas." }
            }
            is LedgerAction.Refund -> {
                val m = movement(action.movementId)
                require(m.row.kind == DocumentKind.NUBANK_CARD && m.row.cents < 0 && !PaymentMatching.isPayment(m.row))
                require(action.date == m.row.date)
                require(action.credits.values.fold(0L, Math::addExact) == abs(m.row.cents)) { "Distribua o valor integral do estorno." }
            }
            is LedgerAction.InvoicePaid -> {
                val account = movement(action.accountMovementId); val card = movement(action.cardMovementId)
                require(account.row.kind == DocumentKind.NUBANK_ACCOUNT && card.row.kind == DocumentKind.NUBANK_CARD)
                require(PaymentMatching.isPayment(account.row) && PaymentMatching.isPayment(card.row))
                require(account.row.cents < 0 && card.row.cents < 0 && account.row.cents == card.row.cents)
                require(card.row.statement == action.reference)
                require(account.pairedMovementId == 0L && card.pairedMovementId == 0L) { "Pagamento já conciliado." }
            }
            is LedgerAction.Postpone -> Unit
        }
        FinanceLedger.apply(snapshot, action) // Validate before appending or changing review status.
        db.financialEvents().append(FinancialEvent().apply { createdAt = System.currentTimeMillis(); payload = LedgerCodec.encode(action) })
        val rows = db.movements().all().associateBy { it.id }
        fun mark(id: Long, status: String, category: String = "", pair: Long = 0) {
            val row = rows.getValue(id); row.status = status; row.category = category; row.pairedMovementId = pair
            row.confirmedAt = System.currentTimeMillis(); db.movements().update(row)
        }
        when (action) {
            is LedgerAction.CardCredit -> mark(action.movementId, "CARD_CREDIT")
            is LedgerAction.FlashCredit -> mark(action.movementId, "FLASH_CREDIT")
            is LedgerAction.Asset -> (action.change as? AssetChange.Transfer)?.let { mark(it.movementId, "ASSET_TRANSFER") }
            is LedgerAction.ResolveConflict -> mark(action.movementId, if (action.keepDistinct) "PENDING" else "IGNORED")
            is LedgerAction.ForecastMatch -> mark(action.movementId, "CONFIRMED", snapshot.planning.expenses.single { it.id == action.expenseId }.category)
            is LedgerAction.Purchase -> action.plan.parts.filter { it.movementId > 0 }.forEach { mark(it.movementId, "CONFIRMED", action.plan.category) }
            is LedgerAction.AdjustLink -> mark(action.movementId, "CONFIRMED", snapshot.purchases.single { it.id == action.purchaseId }.category)
            is LedgerAction.Link -> mark(action.movementId, "CONFIRMED", snapshot.purchases.single { it.id == action.purchaseId }.category)
            is LedgerAction.Payment -> mark(action.movementId, "FAMILY_PAYMENT")
            is LedgerAction.Refund -> mark(action.movementId, "REFUND")
            is LedgerAction.InvoicePaid -> { mark(action.accountMovementId, "PAYMENT", pair = action.cardMovementId); mark(action.cardMovementId, "PAYMENT", pair = action.accountMovementId) }
            else -> Unit
        }
    }
}
