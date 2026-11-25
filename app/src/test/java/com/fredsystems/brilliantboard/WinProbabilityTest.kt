package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.domain.review.WinProbability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WinProbabilityTest {
    @Test
    fun evenPositionIsFiftyPercent() {
        assertEquals(50f, WinProbability.fromCentipawns(0), 0.01f)
    }

    @Test
    fun matchesSpecifiedFormula() {
        val expected = 50.0 + 50.0 * (2.0 / (1.0 + kotlin.math.exp(-0.00368208 * 100.0)) - 1.0)
        assertEquals(expected.toFloat(), WinProbability.fromCentipawns(100), 0.001f)
    }

    @Test
    fun isSymmetricAroundFifty() {
        listOf(25, 100, 350, 900).forEach { cp ->
            assertEquals(100f, WinProbability.fromCentipawns(cp) + WinProbability.fromCentipawns(-cp), 0.01f)
        }
    }

    @Test
    fun mateScoresAreAlmostCertain() {
        assertTrue(WinProbability.fromMate(1) > 99f)
        assertTrue(WinProbability.fromMate(-1) < 1f)
        assertTrue(WinProbability.fromMate(1) > WinProbability.fromMate(3))
    }

    @Test
    fun centipawnInverseIsAccurate() {
        assertEquals(120.0, WinProbability.centipawnsFor(WinProbability.fromCentipawns(120)).toDouble(), 2.0)
    }

    @Test
    fun scoreIsFlippedForOppositeSide() {
        val score = WinProbability.forSide(100, null, whiteToMove = true, perspectiveIsWhite = false)
        assertEquals(100f - WinProbability.fromCentipawns(100), score!!, 0.01f)
    }
}
