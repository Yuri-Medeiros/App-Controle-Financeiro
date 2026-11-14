package br.com.yurimedeiros.meufinanceiro.ledger

/** Removes only active card projections. The original journal remains stored for audit/backup. */
internal object InvoiceReset {
    fun affectedDebts(snapshot: LedgerSnapshot): Set<String> {
        val purchases = snapshot.purchases.map { it.id }.toSet()
        val refunds = snapshot.actions.filterIsInstance<LedgerAction.Refund>().filter { it.purchaseId in purchases }.map { it.movementId }.toSet()
        return snapshot.debts.filter { debt -> debt.purchaseId in purchases || refunds.any { debt.id.startsWith("return:$it:") } }.map { it.id }.toSet()
    }

    fun apply(snapshot: LedgerSnapshot, action: LedgerAction.ResetInvoices): LedgerSnapshot {
        require(action.movementIds.all { it > 0 } && action.documents.keys.all { it.isNotBlank() })
        require(action.movementIds.isNotEmpty() || action.purchaseIds.isNotEmpty() || action.documents.isNotEmpty()) { "Não há faturas para redefinir." }
        require(action.purchaseIds == snapshot.purchases.map { it.id }.toSet()) { "Os dados mudaram. Confira o reset novamente." }
        require(snapshot.purchases.flatMap { it.parts }.filter { it.movementId > 0 }.all { it.movementId in action.movementIds })
        val debts = affectedDebts(snapshot)
        val retained = snapshot.actions.mapNotNull { old -> when (old) {
            is LedgerAction.Purchase -> null
            is LedgerAction.Link -> null
            is LedgerAction.AdjustLink -> null
            is LedgerAction.Refund -> { require(old.movementId in action.movementIds); null }
            is LedgerAction.CardCredit -> { require(old.movementId in action.movementIds); null }
            is LedgerAction.InvoicePaid -> { require(old.cardMovementId in action.movementIds); null }
            is LedgerAction.InvoiceAllocation -> { require(old.cardMovementId in action.movementIds); null }
            is LedgerAction.Payment -> old.copy(allocations = old.allocations.filterKeys { it !in debts }).takeIf { it.allocations.isNotEmpty() }
            is LedgerAction.Postpone -> old.takeIf { it.debtId !in debts }
            is LedgerAction.ResolveConflict -> old.takeIf { it.movementId !in action.movementIds }
            else -> old
        } }
        return snapshot.copy(purchases = emptyList(), debts = snapshot.debts.filter { it.id !in debts },
            actions = retained + action, planning = snapshot.planning.copy(invoiceDates = emptyMap()))
    }
}

internal data class InvoiceResetPreview(
    val action: LedgerAction.ResetInvoices, val installments: Int, val affectedDebts: Int, val mixedTransfers: Int,
) {
    val available get() = action.movementIds.isNotEmpty() || action.purchaseIds.isNotEmpty() || action.documents.isNotEmpty()
}
