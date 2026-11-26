package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.domain.model.EngineLine
import com.fredsystems.brilliantboard.engine.HybridEvaluator
import com.fredsystems.brilliantboard.engine.PositionEvaluator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HybridEvaluatorTest {
    private val fallback = PositionEvaluator { _, _, _, _ ->
        listOf(EngineLine(moves = listOf("e2e4"), cp = 42))
    }

    @Test
    fun usesPrimaryWhenItResponds() = runBlocking {
        val primary = PositionEvaluator { _, _, _, _ -> listOf(EngineLine(cp = 100)) }
        val result = HybridEvaluator(primary, fallback, 1000).evaluate("fen", emptyList(), 12, 3)
        assertEquals(100, result.first().cp)
    }

    @Test
    fun fallsBackWhenPrimaryReturnsNothing() = runBlocking {
        val primary = PositionEvaluator { _, _, _, _ -> emptyList() }
        val result = HybridEvaluator(primary, fallback, 1000).evaluate("fen", emptyList(), 12, 3)
        assertEquals(42, result.first().cp)
    }

    @Test
    fun fallsBackWhenPrimaryTimesOut() = runBlocking {
        val primary = PositionEvaluator { _, _, _, _ ->
            delay(5000)
            listOf(EngineLine(cp = 100))
        }
        val result = HybridEvaluator(primary, fallback, 200).evaluate("fen", emptyList(), 12, 3)
        assertEquals(42, result.first().cp)
    }
}
