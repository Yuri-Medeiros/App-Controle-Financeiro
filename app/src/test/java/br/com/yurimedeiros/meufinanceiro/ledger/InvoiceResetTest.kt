package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.assets.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InvoiceResetTest {
    private val date = LocalDate.of(2026, 11, 9)
    private fun purchase(id: String = "p", movement: Long = 1) = LedgerAction.Purchase(PurchasePlan(id, "Compra", "Casa",
        listOf(SchedulePart(1, date, 20000, movement), SchedulePart(2, date.plusMonths(1), 20000)),
        linkedMapOf(0L to listOf(10000L, 10000L), 7L to listOf(10000L, 10000L)), familyStart = date))
    private fun debt() = LedgerAction.Debt("outside", 7, DebtDirection.RECEIVE, "Fora do cartão", 2000, date)
    private fun reset(ids: Set<Long> = setOf(1), purchases: Set<String> = setOf("p")) = LedgerAction.ResetInvoices(ids, purchases, mapOf("email:card" to date.toString()))
    private fun replay(vararg actions: LedgerAction) = FinanceLedger.replay(actions.toList())

    @Test fun removesCardInstallmentsAndDebtsWithoutTouchingAssetsOrManualDebts() {
        val asset = LedgerAction.Asset(AssetChange.Register(AssetPosition("fgts", "FGTS", AssetKind.FGTS, 12345, 0, date)))
        val settings = LedgerSnapshot().planning.copy(balanceCents = 99999, balanceDate = date.minusDays(1), invoiceDates = mapOf("2026-11" to date))
        val actions = listOf(purchase(), debt(), asset, LedgerAction.Planning(settings))
        val before = FinanceLedger.replay(actions)
        val after = FinanceLedger.apply(before, reset())
        assertTrue(after.purchases.isEmpty())
        assertEquals(listOf("outside"), after.debts.map { it.id })
        assertEquals(before.assets, after.assets)
        assertEquals(settings.copy(invoiceDates = emptyMap()), after.planning)
        assertEquals(actions, before.actions)
        assertFalse(after.actions.any { it is LedgerAction.Purchase })
    }
    @Test fun mixedReceiptKeepsOutsideAllocationAndItsBankSourceReserved() {
        val payment = LedgerAction.Payment(7, DebtDirection.RECEIVE, 10, date, linkedMapOf("p:1:7" to 5000, "outside" to 1000))
        val before = replay(purchase(), debt(), payment)
        val after = FinanceLedger.apply(before, reset())
        assertEquals(1000L, after.debts.single().paidCents)
        assertEquals(1000L, after.debts.single().remaining)
        assertEquals(mapOf("outside" to 1000L), after.actions.filterIsInstance<LedgerAction.Payment>().single().allocations)
        assertTrue(10L in after.usedMovementIds)
        assertEquals(6000L, payment.allocations.values.sum()) // Original event stays intact.
        assertTrue(runCatching { FinanceLedger.apply(after, LedgerAction.Payment(7, DebtDirection.RECEIVE, 10, date, mapOf("outside" to 1000))) }.isFailure)
    }
    @Test fun fullyCardReceiptAndBankPairAreReleasedButManualPaymentIsNot() {
        val before = replay(purchase(), debt(), LedgerAction.Payment(7, DebtDirection.RECEIVE, 10, date, mapOf("p:1:7" to 3000)),
            LedgerAction.Payment(7, DebtDirection.RECEIVE, 11, date, mapOf("outside" to 1000)),
            LedgerAction.InvoicePaid(date, 20, 21), LedgerAction.InvoiceAllocation(date, 20, 21, mapOf(0L to 10000, 7L to 10000)))
        val after = FinanceLedger.apply(before, reset(setOf(1, 21)))
        assertEquals(setOf(11L), after.usedMovementIds)
        assertEquals(1000L, after.debts.single().remaining)
        assertTrue(after.actions.none { it is LedgerAction.InvoicePaid || it is LedgerAction.InvoiceAllocation })
    }
    @Test fun refundReturnAndItsPostponementAreRemovedButOtherPayablesRemain() {
        val before = replay(purchase(), LedgerAction.Payment(7, DebtDirection.RECEIVE, 10, date, mapOf("p:1:7" to 10000)),
            LedgerAction.Refund("p", 5, date, mapOf("p:1:7" to 4000)),
            LedgerAction.Postpone("return:5:p:1:7", date.plusDays(5), "Combinado"),
            LedgerAction.Debt("own-payable", 7, DebtDirection.PAY, "Avulsa", 1000, date))
        val after = FinanceLedger.apply(before, reset(setOf(1, 5)))
        assertEquals(listOf("own-payable"), after.debts.map { it.id })
        assertTrue(after.actions.none { it is LedgerAction.Refund || it is LedgerAction.Postpone })
    }
    @Test fun repeatedResetAndReimportCanReuseTheActualReceiptWithoutOldRefundsLeaking() {
        val first = reset(setOf(1, 5))
        val secondPurchase = purchase("q", 100)
        val second = reset(setOf(100), setOf("q"))
        val events = listOf(purchase(), LedgerAction.Payment(7, DebtDirection.RECEIVE, 10, date, mapOf("p:1:7" to 5000)),
            LedgerAction.Refund("p", 5, date, mapOf("p:1:0" to 1000)), first,
            secondPurchase, LedgerAction.Payment(7, DebtDirection.RECEIVE, 10, date, mapOf("q:1:7" to 5000)), second)
        val restored = FinanceLedger.replay(events.map { LedgerCodec.decode(LedgerCodec.encode(it)) })
        assertTrue(restored.purchases.isEmpty())
        assertTrue(restored.debts.isEmpty())
        assertTrue(restored.usedMovementIds.isEmpty())
        assertEquals(2, restored.actions.filterIsInstance<LedgerAction.ResetInvoices>().size)
    }
    @Test fun incompleteOrStaleResetCannotDropLinkedSources() {
        val before = replay(purchase())
        assertTrue(runCatching { FinanceLedger.apply(before, reset(emptySet())) }.isFailure)
        assertTrue(runCatching { FinanceLedger.apply(before, reset(purchases = emptySet())) }.isFailure)
        assertTrue(runCatching { FinanceLedger.apply(before, reset(purchases = setOf("other"))) }.isFailure)
        assertEquals(1, before.purchases.size)
        assertEquals(2, before.debts.size)
    }
    @Test fun removingCardCreditsLeavesFlashAndBankForecastLinksIntact() {
        val before = replay(purchase(), LedgerAction.CardCredit(5, "Crédito"), LedgerAction.ResolveConflict(6, false),
            LedgerAction.FlashCredit(9, LocalDate.of(2026, 11, 8)))
        val after = FinanceLedger.apply(before, reset(setOf(1, 5, 6)))
        assertEquals(setOf(9L), after.usedMovementIds)
        assertTrue(after.actions.none { it is LedgerAction.ResolveConflict || it is LedgerAction.CardCredit })
        assertTrue(after.actions.any { it is LedgerAction.FlashCredit })
    }
    @Test fun unlinkedCardRowsCanBeResetWithoutAnyPurchaseOrPerson() {
        val action = reset(setOf(11, 12), emptySet())
        val after = FinanceLedger.apply(LedgerSnapshot(), action)
        assertEquals(action, LedgerCodec.decode(LedgerCodec.encode(action)))
        assertTrue(after.purchases.isEmpty())
        assertTrue(after.debts.isEmpty())
        assertTrue(after.usedMovementIds.isEmpty())
    }
}
