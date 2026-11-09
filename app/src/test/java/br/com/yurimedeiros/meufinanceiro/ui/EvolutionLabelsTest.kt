package br.com.yurimedeiros.meufinanceiro.ui

import org.junit.Assert.*
import org.junit.Test

class EvolutionLabelsTest {
    @Test fun clusteredEndpointsKeepLabelsSeparateAndInsideChart() {
        val labels = evolutionLabelPositions(listOf(198f, 197f, 198f, 196f), 28f, 200f, 8f)
        val sorted = labels.sorted()
        assertTrue(sorted.first() >= 14f)
        assertTrue(sorted.last() <= 186f)
        sorted.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 36f) }
    }
    @Test fun separatedEndpointsStayAtTheirOriginalHeights() {
        assertEquals(listOf(20f, 100f, 180f), evolutionLabelPositions(listOf(20f, 100f, 180f), 20f, 200f, 8f))
    }
    @Test fun largerFontLabelsStayInOrderWithoutOverlap() {
        val positions = evolutionLabelPositions(listOf(2f, 4f, 3f, 0f), 56f, 272f, 8f)
        assertTrue(positions[3] < positions[0] && positions[0] < positions[2] && positions[2] < positions[1])
        positions.sorted().zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 64f) }
        assertTrue(positions.min() >= 28f && positions.max() <= 244f)
    }
}
