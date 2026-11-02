package br.com.yurimedeiros.meufinanceiro.ledger

import br.com.yurimedeiros.meufinanceiro.domain.PurchaseSplit
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinanceLedgerTest {
    private val due = LocalDate.of(2026, 11, 9)
    private fun purchase() = LedgerAction.Purchase(PurchasePlan("p", "Compra", "Casa",
        listOf(SchedulePart(1, due, 20000, 1), SchedulePart(2, due.plusMonths(1), 20000)),
        linkedMapOf(0L to listOf(10000L, 10000L), 1L to listOf(10000L, 10000L))))
    private fun debt(id: String, amount: Long, direction: DebtDirection = DebtDirection.RECEIVE) = LedgerAction.Debt(id, 1, direction, id, amount, due)
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { } }

    @Test fun bankPaymentDoesNotSettleFamilyDebt() {
        val s = FinanceLedger.replay(listOf(purchase(), LedgerAction.InvoicePaid(due, 8, 9)))
        assertEquals(20000L, s.debts.sumOf { it.remaining })
    }
    @Test fun pixCanSettleMultipleDebtsPartially() {
        val s = FinanceLedger.replay(listOf(debt("a", 30000), debt("b", 15000), debt("c", 10000),
            LedgerAction.Payment(1, DebtDirection.RECEIVE, 8, due, linkedMapOf("a" to 30000, "b" to 15000, "c" to 5000))))
        assertEquals(listOf(0L, 0L, 5000L), s.debts.map { it.remaining })
    }
    @Test fun receivableAndPayableNeverNetEachOther() {
        val s = FinanceLedger.replay(listOf(debt("receive", 20000), debt("pay", 20000, DebtDirection.PAY)))
        assertEquals(2, s.debts.size)
        assertEquals(40000L, s.debts.sumOf { it.remaining })
    }
    @Test fun refundAfterPaymentCreatesSeparatePayable() {
        val s = FinanceLedger.replay(listOf(purchase(),
            LedgerAction.Payment(1, DebtDirection.RECEIVE, 2, due, mapOf("p:1:1" to 10000)),
            LedgerAction.Refund("p", 3, due, mapOf("p:1:1" to 4000))))
        assertEquals(10000L, s.debts.single { it.id == "p:1:1" }.paidCents)
        assertEquals(4000L, s.debts.single { it.direction == DebtDirection.PAY }.remaining)
        assertEquals(10000L, s.debts.single { it.id == "p:2:1" }.remaining)
        assertEquals(20000L, s.purchases.single().parts[1].cents)
    }
    @Test fun successiveRefundsCreateOnlyIncrementalReturn() {
        val s = FinanceLedger.replay(listOf(purchase(),
            LedgerAction.Payment(1, DebtDirection.RECEIVE, 2, due, mapOf("p:1:1" to 10000)),
            LedgerAction.Refund("p", 3, due, mapOf("p:1:1" to 3000)), LedgerAction.Refund("p", 4, due, mapOf("p:1:1" to 2000))))
        assertEquals(5000L, s.debts.filter { it.direction == DebtDirection.PAY }.sumOf { it.remaining })
    }
    @Test fun refundDoesNotCancelFutureParts() {
        val s = FinanceLedger.replay(listOf(purchase(), LedgerAction.Refund("p", 2, due, mapOf("p:1:1" to 10000))))
        assertEquals(10000L, s.debts.single { it.id == "p:2:1" }.remaining)
        assertEquals(0L, s.debts.single { it.id == "p:1:1" }.remaining)
    }
    @Test fun ownRefundDoesNotCreateFamilyDebt() {
        val s = FinanceLedger.replay(listOf(purchase(), LedgerAction.Refund("p", 2, due, mapOf("p:1:0" to 5000))))
        assertEquals(2, s.debts.size)
        assertTrue(s.debts.none { it.personId == 0L })
    }
    @Test fun rejectDuplicateImportedInstallmentLink() {
        val s = FinanceLedger.replay(listOf(purchase(), LedgerAction.Link("p", 2, 2)))
        assertEquals(1, s.purchases.size)
        rejects { FinanceLedger.apply(s, LedgerAction.Link("p", 2, 3)) }
        rejects { FinanceLedger.apply(s, LedgerAction.Payment(1, DebtDirection.RECEIVE, 2, due, mapOf("p:1:1" to 1))) }
    }
    @Test fun cannotReusePaymentMovement() {
        val s = FinanceLedger.replay(listOf(debt("a", 50000), LedgerAction.Payment(1, DebtDirection.RECEIVE, 2, due, mapOf("a" to 10000))))
        rejects { FinanceLedger.apply(s, LedgerAction.Payment(1, DebtDirection.RECEIVE, 2, due, mapOf("a" to 10000))) }
    }
    @Test fun rejectOverpayAndWrongDirectionAndWrongPerson() {
        val s = FinanceLedger.replay(listOf(debt("a", 100)))
        rejects { FinanceLedger.apply(s, LedgerAction.Payment(1, DebtDirection.RECEIVE, 2, due, mapOf("a" to 101))) }
        rejects { FinanceLedger.apply(s, LedgerAction.Payment(1, DebtDirection.PAY, 2, due, mapOf("a" to 50))) }
        rejects { FinanceLedger.apply(s, LedgerAction.Payment(2, DebtDirection.RECEIVE, 2, due, mapOf("a" to 50))) }
    }
    @Test fun rejectRefundLargerThanOriginalAcrossEvents() {
        val s = FinanceLedger.replay(listOf(purchase(), LedgerAction.Refund("p", 2, due, mapOf("p:1:1" to 9000))))
        rejects { FinanceLedger.apply(s, LedgerAction.Refund("p", 3, due, mapOf("p:1:1" to 1001))) }
    }
    @Test fun dueChangePreservesHistoryAndPayments() {
        val actions = listOf(debt("a", 10000), LedgerAction.Payment(1, DebtDirection.RECEIVE, 2, due, mapOf("a" to 5000)), LedgerAction.Postpone("a", due.plusDays(10), "Combinado"))
        val s = FinanceLedger.replay(actions)
        assertEquals(5000L, s.debts.single().remaining)
        assertEquals(due.plusDays(10), s.debts.single().due)
        assertEquals(due, (s.actions.first() as LedgerAction.Debt).due)
    }
    @Test fun cannotPostponeCardInstallment() {
        val s = FinanceLedger.replay(listOf(purchase()))
        rejects { FinanceLedger.apply(s, LedgerAction.Postpone("p:1:1", due.plusDays(2), "Mudar")) }
    }
    @Test fun sharesMustCloseEveryInstallment() {
        val p = purchase().plan
        rejects { FinanceLedger.replay(listOf(LedgerAction.Purchase(p.copy(shares = mapOf(0L to listOf(1L, 1L)))))) }
    }
    @Test fun historicalSharesDoNotAutomaticallyBecomeOutstandingDebts() {
        val p = purchase().plan.copy(familyOpening = setOf(2), familyStart = due)
        val s = FinanceLedger.replay(listOf(LedgerAction.Purchase(p)))
        assertEquals(1, s.debts.size)
        assertEquals(2, s.debts.single().number)
        assertEquals(10000L, s.purchases.single().shares.getValue(1L)[0])
    }
    @Test fun confirmedOpeningBalanceNeverInventsPriorPaymentsOrRefundPayables() {
        val p = purchase().plan.copy(openingBalances = mapOf("1:1" to 8000L))
        val s = FinanceLedger.replay(listOf(LedgerAction.Purchase(p), LedgerAction.Refund("p", 2, due, mapOf("p:1:1" to 10000))))
        assertEquals(0L, s.debts.single { it.number == 1 }.remaining)
        assertEquals(0L, s.debts.single { it.number == 1 }.paidCents)
        assertTrue(s.debts.none { it.direction == DebtDirection.PAY })
        assertEquals(10000L, s.purchases.single().shares.getValue(1L)[0])
    }
    @Test fun largestRemaindersPreserveEveryCentAndOrder() {
        assertEquals(listOf(3334L, 3333L, 3333L), PurchaseSplit.proportionally(10000, listOf(1, 1, 1)))
        assertEquals(listOf(3334L, 6667L), PurchaseSplit.proportionally(10001, listOf(1, 2)))
    }
    @Test fun importedDifferenceRequiresExplicitAdjustmentAndPreservesOriginalEvent() {
        val original = purchase()
        val s = FinanceLedger.replay(listOf(original, LedgerAction.AdjustLink("p", 2, 2, 20001, due.plusMonths(1), linkedMapOf(0L to 10001L, 1L to 10000L))))
        assertEquals(20001L, s.purchases.single().parts[1].cents)
        assertEquals(20000L, (s.actions.first() as LedgerAction.Purchase).plan.parts[1].cents)
        assertEquals(2, s.debts.size)
    }
    @Test fun adjustmentCannotRewriteAnAlreadyPaidPart() {
        val s = FinanceLedger.replay(listOf(purchase(), LedgerAction.Payment(1, DebtDirection.RECEIVE, 2, due, mapOf("p:2:1" to 1))))
        rejects { FinanceLedger.apply(s, LedgerAction.AdjustLink("p", 2, 3, 20001, due, mapOf(0L to 10001L, 1L to 10000L))) }
    }
    @Test fun refundSuggestionUsesRemainingSharesAndNeverExceedsSelectedParts() {
        val s = FinanceLedger.replay(listOf(purchase(), LedgerAction.Refund("p", 2, due, mapOf("p:1:0" to 10000))))
        assertEquals(mapOf("p:1:1" to 9999L), FinanceLedger.suggestRefund(s, "p", setOf(1), 9999))
        rejects { FinanceLedger.suggestRefund(s, "p", setOf(1), 10001) }
        val next = FinanceLedger.suggestRefund(s, "p", setOf(2), 10001)
        assertEquals(10001L, next.values.sum())
        assertEquals(5001L, next["p:2:0"])
    }
    @Test fun moneyRejectsFractionsOfACentAndPreservesLargePrecision() {
        assertEquals(123456L, FinanceLedger.money("1.234,56"))
        assertEquals(9007199254740993L, FinanceLedger.money("90071992547409.93"))
        assertTrue(runCatching { FinanceLedger.money("1,001") }.isFailure)
        assertTrue(runCatching { FinanceLedger.money("-1") }.isFailure)
    }
}
