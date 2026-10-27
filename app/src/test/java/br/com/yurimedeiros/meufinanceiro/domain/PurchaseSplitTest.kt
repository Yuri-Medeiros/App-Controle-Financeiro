package br.com.yurimedeiros.meufinanceiro.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseSplitTest {
    private fun percentages(vararg values: String) = values.map(::BigDecimal)

    @Test fun approvedEqualSplitPreservesEveryCent() {
        assertEquals(listOf(3334L, 3333L, 3333L), PurchaseSplit.equally(10000, 3))
        assertEquals(listOf(1L, 1L, 0L), PurchaseSplit.equally(2, 3))
    }

    @Test fun largestRemainderTakesPriorityOverParticipantOrder() {
        assertEquals(listOf(0L, 1L, 0L), PurchaseSplit.byPercentages(1, percentages("20", "50", "30")))
        assertEquals(listOf(1L, 1L, 0L), PurchaseSplit.byPercentages(2, percentages("33.33", "33.34", "33.33")))
    }

    @Test fun tiedRemaindersUseRecordedOrderAndZeroPercentReceivesNothing() {
        assertEquals(listOf(1L, 0L, 0L), PurchaseSplit.byPercentages(1, percentages("50.0", "50", "0")))
        assertEquals(listOf(0L, 1L, 0L), PurchaseSplit.byPercentages(1, percentages("0", "50", "50")))
    }

    @Test fun largeTotalsDoNotOverflowDuringMultiplication() {
        val result = PurchaseSplit.byPercentages(Long.MAX_VALUE, percentages("50", "50"))
        assertEquals(listOf(4611686018427387904L, 4611686018427387903L), result)
        assertEquals(Long.MAX_VALUE, result.sum())
    }

    @Test fun zeroTotalIsValid() {
        assertEquals(listOf(0L, 0L), PurchaseSplit.equally(0, 2))
    }

    @Test fun invalidInputsAreRejectedInsteadOfChangingThePurchase() {
        assertThrows(IllegalArgumentException::class.java) { PurchaseSplit.equally(-1, 2) }
        assertThrows(IllegalArgumentException::class.java) { PurchaseSplit.equally(1, 0) }
        for (input in listOf(emptyList(), percentages("40", "50"), percentages("110", "-10"))) {
            assertThrows(IllegalArgumentException::class.java) { PurchaseSplit.byPercentages(100, input) }
        }
    }

    @Test fun allSmallEqualSplitsPreserveTotalAndDifferByAtMostOneCent() {
        for (count in 1..20) for (total in 0L..200L) {
            val parts = PurchaseSplit.equally(total, count)
            assertEquals(total, parts.sum())
            assertTrue(parts.all { it >= 0 })
            assertTrue(parts.max() - parts.min() <= 1)
        }
    }
}
