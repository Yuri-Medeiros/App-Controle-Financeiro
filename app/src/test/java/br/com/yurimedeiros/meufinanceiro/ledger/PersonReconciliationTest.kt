package br.com.yurimedeiros.meufinanceiro.ledger

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PersonReconciliationTest {
    private val december = LocalDate.of(2026, 12, 8)
    private val january = LocalDate.of(2027, 1, 8)
    private val parts = listOf(SchedulePart(1, december, 30000, 42), SchedulePart(2, january, 30000))
    private val shares = linkedMapOf(0L to listOf(10000L, 10000L), 7L to listOf(10000L, 10000L), 8L to listOf(10000L, 10000L))
    private fun configured() = FinanceLedger.replay(listOf(LedgerAction.PersonReconciliation(7, december), LedgerAction.PersonReconciliation(8, january)))

    @Test fun individualDatesGenerateOnlyEligibleDebtsWithoutChangingTheInvoiceShares() {
        val before = configured()
        val plan = PersonReconciliation.purchase(before, "p", "Compra", "Casa", parts, shares)
        val after = FinanceLedger.apply(before, LedgerAction.Purchase(plan))
        assertEquals(shares, plan.shares)
        assertEquals(listOf("p:1:7", "p:2:7", "p:2:8"), after.debts.map { it.id })
        assertEquals(0L, plan.openingBalances["1:8"])
        assertEquals(30000L, plan.parts.first().cents)
    }
    @Test fun changingTheDatePreservesExistingDebtsAndRecordedReceipts() {
        val purchase = PersonReconciliation.purchase(configured(), "p", "Compra", "Casa", parts, shares)
        val paid = FinanceLedger.replay(configured().actions + LedgerAction.Purchase(purchase) +
            LedgerAction.Payment(7, DebtDirection.RECEIVE, 99, december, mapOf("p:1:7" to 2500L)))
        val changed = FinanceLedger.apply(paid, LedgerAction.PersonReconciliation(7, january))
        assertEquals(paid.debts, changed.debts)
        assertEquals(paid.purchases, changed.purchases)
        assertEquals(january, PersonReconciliation.start(changed, 7))
        assertEquals(january, PersonReconciliation.start(changed, 8))
    }
    @Test fun legacyPurchaseDateIsUsedUntilAnIndividualSettingIsSaved() {
        val legacy = PurchasePlan("old", "Anterior", "Casa", parts, mapOf(7L to listOf(30000L, 30000L)), familyStart = december)
        val before = FinanceLedger.apply(LedgerSnapshot(), LedgerAction.Purchase(legacy))
        assertEquals(december, PersonReconciliation.start(before, 7))
        assertNull(PersonReconciliation.start(before, 8))
        assertEquals(january, PersonReconciliation.start(FinanceLedger.apply(before, LedgerAction.PersonReconciliation(7, january)), 7))
    }
    @Test fun codecReplayAndInvoiceResetRetainIndividualDates() {
        val before = FinanceLedger.replay(configured().actions.map { LedgerCodec.decode(LedgerCodec.encode(it)) })
        val plan = PersonReconciliation.purchase(before, "p", "Compra", "Casa", parts, shares)
        val withPurchase = FinanceLedger.apply(before, LedgerAction.Purchase(plan))
        val resetAction = LedgerAction.ResetInvoices(setOf(42), setOf("p"), emptyMap())
        val reset = FinanceLedger.apply(withPurchase, resetAction)
        assertEquals(before.reconciliationStarts, reset.reconciliationStarts)
        assertEquals(before.reconciliationStarts, FinanceLedger.replay((withPurchase.actions + resetAction).map { LedgerCodec.decode(LedgerCodec.encode(it)) }).reconciliationStarts)
    }
    @Test fun missingDatesAreNotInventedAndOwnPurchasesNeedNoReconciliation() {
        assertTrue(runCatching { PersonReconciliation.purchase(LedgerSnapshot(), "p", "Compra", "Casa", parts, shares) }.isFailure)
        val own = PersonReconciliation.purchase(LedgerSnapshot(), "p", "Compra", "Casa", parts, mapOf(0L to listOf(30000L, 30000L)))
        assertTrue(FinanceLedger.apply(LedgerSnapshot(), LedgerAction.Purchase(own)).debts.isEmpty())
        assertTrue(runCatching { FinanceLedger.apply(LedgerSnapshot(), LedgerAction.PersonReconciliation(0, december)) }.isFailure)
    }
}
