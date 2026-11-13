package br.com.yurimedeiros.meufinanceiro.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class DonutSelectionTest {
    private fun at(values: List<Long>, degrees: Double): Int? {
        val angle = Math.toRadians(degrees)
        return donutSliceAt(values, (100 + sin(angle) * 90).toFloat(), (100 - cos(angle) * 90).toFloat(), 200f, 200f)
    }
    @Test fun picksEachAreaClockwiseFromTheTop() {
        val values = listOf(100L, 100L, 100L, 100L)
        assertEquals(0, at(values, 45.0))
        assertEquals(1, at(values, 135.0))
        assertEquals(2, at(values, 225.0))
        assertEquals(3, at(values, 315.0))
        assertEquals(0, donutSliceAt(values, 100f, 10f, 200f, 200f))
    }
    @Test fun respectsUnequalAreasAndZeroValues() {
        assertEquals(0, at(listOf(60L, 30L, 10L), 180.0))
        assertEquals(1, at(listOf(60L, 30L, 10L), 270.0))
        assertEquals(2, at(listOf(60L, 30L, 10L), 350.0))
        assertEquals(1, at(listOf(0L, 100L, 0L), 45.0))
        assertNull(at(listOf(0L, 0L), 45.0))
        assertNull(at(emptyList(), 45.0))
    }
    @Test fun centerAndOutsideTheRingDoNotSelectAnArea() {
        val values = listOf(100L)
        assertNull(donutSliceAt(values, 100f, 100f, 200f, 200f))
        assertNull(donutSliceAt(values, 100f, 50f, 200f, 200f))
        assertNull(donutSliceAt(values, 205f, 100f, 200f, 200f))
        assertNull(donutSliceAt(values, Float.NaN, 10f, 200f, 200f))
        assertEquals(0, donutSliceAt(values, 150f, 10f, 300f, 200f))
    }
}
