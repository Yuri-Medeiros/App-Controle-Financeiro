package br.com.yurimedeiros.meufinanceiro.ui

import androidx.compose.ui.text.AnnotatedString
import br.com.yurimedeiros.meufinanceiro.ledger.FinanceLedger
import org.junit.Assert.*
import org.junit.Test

class MoneyInputTest {
    @Test fun groupsThousandsWithoutRoundingOrChangingTheDecimalPart() {
        for ((raw, displayed) in listOf("100000,00" to "100.000,00", "1234,5" to "1.234,5",
            "1000000" to "1.000.000", "-1234,56" to "-1.234,56", "0,01" to "0,01",
            "90071992547409,93" to "90.071.992.547.409,93", "1234,567" to "1.234,567")) {
            val result = MoneyInputVisualTransformation.filter(AnnotatedString(raw))
            assertEquals(displayed, result.text.text)
            assertEquals(raw, normalizeMoneyInput(result.text.text))
        }
        assertEquals(9007199254740993L, FinanceLedger.money(checkNotNull(normalizeMoneyInput("90.071.992.547.409,93"))))
    }

    @Test fun pastingFormattedValuesKeepsExactCentsAndHandlesDecimalDot() {
        for (pasted in listOf("100.000,00", "R$\u00a0100.000,00", "100000.00", "100,000.00", "100.000")) {
            val normalized = checkNotNull(normalizeMoneyInput(pasted))
            assertEquals(10000000L, FinanceLedger.money(normalized))
        }
        assertEquals("-1000,50", normalizeMoneyInput("-1.000,50"))
        assertEquals("-1000,50", normalizeMoneyInput("-R$\u00a01.000,50"))
        assertNull(normalizeMoneyInput("1,0.00"))
        assertNull(normalizeMoneyInput("12/11/2026"))
        assertNull(normalizeMoneyInput("abc"))
        assertNull(normalizeMoneyInput("1,23,45"))
        val tooPrecise = checkNotNull(normalizeMoneyInput("1.234,567"))
        assertTrue(runCatching { FinanceLedger.money(tooPrecise) }.isFailure)
    }

    @Test fun cursorAndSelectionsStayInRangeAcrossInsertedSeparators() {
        for (raw in listOf("", "+", "-", ",", "1,", "1234", "1234567,89", "-1234567,89", "90071992547409,93")) {
            val result = MoneyInputVisualTransformation.filter(AnnotatedString(raw))
            val mapping = result.offsetMapping
            val forward = (0..raw.length).map(mapping::originalToTransformed)
            val backward = (0..result.text.length).map(mapping::transformedToOriginal)
            assertEquals(forward.sorted(), forward)
            assertEquals(backward.sorted(), backward)
            assertTrue(forward.all { it in 0..result.text.length })
            assertTrue(backward.all { it in 0..raw.length })
            for (offset in 0..raw.length) assertEquals(offset, mapping.transformedToOriginal(mapping.originalToTransformed(offset)))
        }
        val mapping = MoneyInputVisualTransformation.filter(AnnotatedString("1234,56")).offsetMapping
        assertEquals(2, mapping.originalToTransformed(1))
        assertEquals(1, mapping.transformedToOriginal(1))
        assertEquals(1, mapping.transformedToOriginal(2))
    }

    @Test fun partialTypingAndDeletionRemainEditable() {
        for (raw in listOf("", "1", "10", "100", "1000", "10000", "100000", "100000,", "100000,0", "100000,00")) {
            assertEquals(raw, normalizeMoneyInput(raw))
            assertEquals(raw, normalizeMoneyInput(MoneyInputVisualTransformation.filter(AnnotatedString(raw)).text.text))
        }
        assertEquals("1,", normalizeMoneyInput("1."))
        assertEquals("0,5", normalizeMoneyInput("0.5"))
    }
}
