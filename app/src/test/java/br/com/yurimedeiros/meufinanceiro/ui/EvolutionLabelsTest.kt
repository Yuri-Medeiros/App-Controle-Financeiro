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
    @Test fun captionsPreferAboveTheirActualEndpoints() {
        val targets = listOf(EvolutionLabelTarget(300f, 80f, 80f, 24f), EvolutionLabelTarget(300f, 180f, 80f, 24f))
        val boxes = evolutionLabelBoxes(targets, 320f, 260f, 8f)
        boxes.forEachIndexed { index, box ->
            assertEquals(targets[index].x, box.right, 0.01f)
            assertEquals(targets[index].y - 8f, box.bottom, 0.01f)
        }
    }

    @Test fun closeEndpointsMoveCaptionsBackwardInsteadOfOverlapping() {
        val targets = listOf(EvolutionLabelTarget(300f, 100f, 90f, 24f), EvolutionLabelTarget(300f, 102f, 90f, 24f))
        val boxes = evolutionLabelBoxes(targets, 320f, 260f, 8f)
        assertTrue(boxes[1].right <= boxes[0].x - 8f)
        assertFalse(boxes[0].overlaps(boxes[1], 8f))
        assertEquals(94f, boxes[1].bottom, 0.01f)
    }

    @Test fun anotherSeriesBlocksAboveAndMovesCaptionBehindTheTip() {
        val target = EvolutionLabelTarget(300f, 100f, 80f, 24f)
        val lines = listOf(EvolutionLineSegment(1, 0f, 75f, 320f, 75f), EvolutionLineSegment(0, 0f, 100f, 300f, 100f))
        val box = evolutionLabelBoxes(listOf(target), 320f, 200f, 8f, lines).single()
        assertEquals(292f, box.right, 0.01f)
        assertTrue(box.y < target.y && box.bottom > target.y)
        assertFalse(evolutionLineCrossesLabel(lines[0], box))
    }

    @Test fun crowdedCaptionsAndLargerFontsStayInsideThePlot() {
        for ((count, labelHeight, height) in listOf(Triple(6, 32f, 260f), Triple(4, 64f, 320f))) {
            val targets = List(count) { EvolutionLabelTarget(310f, 70f + it, 120f, labelHeight) }
            val boxes = evolutionLabelBoxes(targets, 320f, height, 8f)
            assertEquals(count, boxes.size)
            boxes.forEach { box ->
                assertTrue(box.x >= 0 && box.y >= 0 && box.right <= 320f && box.bottom <= height)
            }
            for (first in boxes.indices) for (second in first + 1 until boxes.size) {
                assertFalse(boxes[first].overlaps(boxes[second], 8f))
            }
        }
    }

    @Test fun earlyEndpointsKeepCaptionsInBoundsAndEmptyDataNeedsNoPlacement() {
        val box = evolutionLabelBoxes(listOf(EvolutionLabelTarget(10f, 100f, 100f, 32f)), 320f, 200f, 8f).single()
        assertEquals(0f, box.x, 0.01f)
        assertEquals(92f, box.bottom, 0.01f)
        assertTrue(evolutionLabelBoxes(emptyList(), 0f, 0f, 8f).isEmpty())
    }

    @Test fun detectsCrossingSegmentsAndSinglePointsWithoutRejectingDistantLines() {
        val box = EvolutionLabelBox(100f, 50f, 80f, 30f)
        assertTrue(evolutionLineCrossesLabel(EvolutionLineSegment(0, 0f, 60f, 300f, 60f), box))
        assertTrue(evolutionLineCrossesLabel(EvolutionLineSegment(0, 120f, 60f, 120f, 60f), box))
        assertTrue(evolutionLineCrossesLabel(EvolutionLineSegment(0, 90f, 40f, 190f, 90f), box))
        assertFalse(evolutionLineCrossesLabel(EvolutionLineSegment(0, 0f, 40f, 300f, 40f), box))
        assertFalse(evolutionLineCrossesLabel(EvolutionLineSegment(0, 90f, 60f, 90f, 60f), box))
    }

}
