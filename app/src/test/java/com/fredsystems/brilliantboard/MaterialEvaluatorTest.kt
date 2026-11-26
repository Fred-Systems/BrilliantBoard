package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.engine.MaterialEvaluator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialEvaluatorTest {
    @Test
    fun evenPositionIsZero() = runBlocking {
        assertEquals(0, MaterialEvaluator.estimate(Position.START.fen()))
    }

    @Test
    fun queenAdvantageIsDetected() = runBlocking {
        val fen = "rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val cp = MaterialEvaluator.estimate(fen)
        assertTrue(cp > 0)
    }

    @Test
    fun scoreIsRelativeToSideToMove() = runBlocking {
        val fen = "4k3/8/8/8/8/8/8/4KQ2 w - - 0 1"
        val white = MaterialEvaluator.estimate(fen)
        val black = MaterialEvaluator.estimate(fen.replace(" w ", " b "))
        assertEquals(white, -black)
    }
}
