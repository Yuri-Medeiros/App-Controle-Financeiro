package br.com.yurimedeiros.meufinanceiro.domain

import org.junit.Assert.*
import org.junit.Test

class PaymentDivisionTest {
    @Test fun startsWithOwnShareAndRejectsNoPeople() {
        assertEquals(listOf(0L), PaymentDivision(4090).people)
        assertEquals(listOf(4090L), PaymentDivision(4090).amounts())
        assertTrue(runCatching { PaymentDivision(4090, emptyList()) }.isFailure)
    }
    @Test fun fixedMoneyEntriesStayFixedAndRemainderIsSuggestedExactly() {
        var split = PaymentDivision(10001, listOf(0, 7, 8), DivisionMode.AMOUNT).edit(7, 3000)
        assertEquals(listOf(3501L, 3000L, 3500L), split.amounts())
        split = split.edit(0, 4000)
        assertEquals(listOf(4000L, 3000L, 3001L), split.amounts())
        assertEquals(mapOf(7L to 3000L, 0L to 4000L), split.fixed)
    }
    @Test fun fixedPercentagesFillUntouchedPeopleAndPreserveCents() {
        var split = PaymentDivision(4090, listOf(0, 7, 8), DivisionMode.PERCENT).edit(0, 3333)
        assertEquals(listOf(3333L, 3334L, 3333L), split.percentages())
        assertEquals(4090L, split.amounts().sum())
        split = split.edit(7, 5000)
        assertEquals(listOf(3333L, 5000L, 1667L), split.percentages())
        assertEquals(4090L, split.amounts().sum())
    }
    @Test fun excessiveAndIncompleteFullyFixedValuesNeverSilentlyOverwritePeople() {
        val over = PaymentDivision(10000, listOf(0, 7), DivisionMode.PERCENT).edit(0, 11000)
        assertTrue(runCatching { over.amounts() }.isFailure)
        assertEquals(11000L, over.fixed[0])
        val under = PaymentDivision(10000, listOf(0, 7), DivisionMode.AMOUNT).edit(0, 1000).edit(7, 1000)
        assertTrue(runCatching { under.amounts() }.isFailure)
        assertEquals(listOf(1000L, 1000L), under.units())
    }
    @Test fun switchingModesSeedsTheSameAllocationAndClearsManualPins() {
        val initial = PaymentDivision(10000, listOf(0, 7), DivisionMode.AMOUNT).edit(0, 2500)
        val percent = initial.mode(DivisionMode.PERCENT)
        assertEquals(initial.amounts(), percent.amounts())
        assertEquals(listOf(2500L, 7500L), percent.percentages())
        assertTrue(percent.fixed.isEmpty())
        assertEquals(listOf(5000L, 5000L), percent.mode(DivisionMode.EQUAL).amounts())
    }
}
