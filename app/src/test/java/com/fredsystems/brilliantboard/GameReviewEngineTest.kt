package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.domain.model.EngineLine
import com.fredsystems.brilliantboard.domain.model.GameResult
import com.fredsystems.brilliantboard.domain.model.MoveRecord
import com.fredsystems.brilliantboard.domain.model.MoveBadge
import com.fredsystems.brilliantboard.domain.review.GameReviewEngine
import com.fredsystems.brilliantboard.engine.PositionEvaluator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameReviewEngineTest {
    private class FixedEvaluator(private val cp: Int) : PositionEvaluator {
        override suspend fun evaluate(fen: String, moves: List<String>, depth: Int, multiPv: Int) =
            listOf(EngineLine(moves = listOf("e2e4"), cp = cp, depth = depth))
    }

    @Test
    fun producesReportWithGraphAndBadges() = runBlocking {
        val moves = listOf(
            MoveRecord(1, "e4", "e2e4", Position.START.apply(Position.START.parseUci("e2e4")!!).fen(), side = com.fredsystems.brilliantboard.domain.model.Side.WHITE),
            MoveRecord(2, "e5", "e7e5", Position.START.apply(Position.START.parseUci("e2e4")!!).apply(
                Position.fromFen(Position.START.apply(Position.START.parseUci("e2e4")!!).fen()).parseUci("e7e5")!!
            ).fen(), side = com.fredsystems.brilliantboard.domain.model.Side.BLACK),
        )
        val game = testGame(moves = moves, result = GameResult.WIN)

        val report = GameReviewEngine.analyse(game, FixedEvaluator(40), depth = 12, multiPv = 1)

        assertEquals(2, report.reviews.size)
        assertEquals(2, report.evalGraph.size)
        assertEquals("g1", report.gameId)
        assertTrue(report.whiteAccuracy >= 0f && report.whiteAccuracy <= 100f)
        assertTrue(report.blackAccuracy >= 0f && report.blackAccuracy <= 100f)
        assertTrue(report.reviews.all { it.badge is MoveBadge })
        assertTrue(report.whitePerformanceElo > 0)
    }

    @Test
    fun handlesMissingEvaluationsGracefully() = runBlocking {
        val game = testGame(moves = emptyList())
        val report = GameReviewEngine.analyse(game, FixedEvaluator(0), depth = 12)
        assertTrue(report.reviews.isEmpty())
        assertTrue(report.blunders == 0)
    }

    @Test
    fun badgeForLossIsBlunderForLargeLoss() {
        assertEquals(MoveBadge.BLUNDER, GameReviewEngine.badgeFor(35f))
        assertEquals(MoveBadge.GOOD, GameReviewEngine.badgeFor(3f))
    }
}
