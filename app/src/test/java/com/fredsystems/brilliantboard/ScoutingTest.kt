package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.domain.model.ClockInfo
import com.fredsystems.brilliantboard.domain.model.GameResult
import com.fredsystems.brilliantboard.domain.model.Side
import com.fredsystems.brilliantboard.domain.model.Termination
import com.fredsystems.brilliantboard.domain.scouting.ClockIQ
import com.fredsystems.brilliantboard.domain.scouting.GameFeatureExtractor
import com.fredsystems.brilliantboard.domain.scouting.OpponentGameplan
import com.fredsystems.brilliantboard.domain.scouting.OpeningLeakFinder
import com.fredsystems.brilliantboard.domain.scouting.TiltAnalytics
import com.fredsystems.brilliantboard.domain.scouting.Wdl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoutingTest {
    @Test
    fun wdlScoreWeightsDraws() {
        assertEquals(50f, Wdl(1, 0, 1).scorePercent, 0.01f)
        assertEquals(75f, Wdl(3, 0, 1).scorePercent, 0.01f)
        assertEquals(75f, Wdl(1, 1, 0).scorePercent, 0.01f)
    }

    @Test
    fun openingLeakFinderRanksByScore() {
        val strong = testGame(id = "a", eco = "B01", result = GameResult.WIN)
        val weak = testGame(id = "b", eco = "C60", result = GameResult.LOSS)
        val weakDraw = testGame(id = "c", eco = "C60", result = GameResult.DRAW)
        val stats = OpeningLeakFinder.analyze(
            listOf(strong, weak, weakDraw).map { GameFeatureExtractor.extract(it, Side.WHITE) },
            Side.WHITE,
            minGames = 1,
        )
        assertEquals("C60", stats.first().eco)
        assertEquals(25f, stats.first().wdl.scorePercent, 0.01f)
        assertEquals(2, stats.first().games)
    }

    @Test
    fun clockIqDetectsTimeTroubleAndBerserk() {
        val clocks = listOf(280, 260, 20, 295, 290)
        val moves = clocks.mapIndexed { index, remaining ->
            com.fredsystems.brilliantboard.domain.model.MoveRecord(
                ply = index * 2 + 1,
                san = "e4",
                uci = "e2e4",
                fen = com.fredsystems.brilliantboard.domain.chess.Position.START.fen(),
                side = Side.WHITE,
                clockSeconds = remaining,
            )
        }
        val game = testGame(
            result = GameResult.LOSS,
            termination = Termination.TIMEOUT,
            clocks = ClockInfo(initialSeconds = 300, incrementSeconds = 0, whiteClocks = clocks),
            moves = moves,
        )
        val features = listOf(GameFeatureExtractor.extract(game, Side.WHITE, blunderPlies = setOf(5, 9)))
        val stats = ClockIQ.analyze(features, Side.WHITE)
        assertEquals(50f, stats.timeTroubleBlunderRate, 0.01f)
        assertEquals(50f, stats.berserkRate, 0.01f)
        assertEquals(100f, stats.timeoutLossRate, 0.01f)
        assertEquals(5, stats.totalMoves)
    }

    @Test
    fun tiltAnalyticsMeasuresPostLossDrop() {
        val games = listOf(
            testGame(id = "1", result = GameResult.LOSS, playedAt = 1),
            testGame(id = "2", result = GameResult.LOSS, playedAt = 2),
            testGame(id = "3", result = GameResult.LOSS, playedAt = 3),
            testGame(id = "4", result = GameResult.WIN, playedAt = 4),
        )
        val features = games.map { GameFeatureExtractor.extract(it, Side.WHITE) }
        val tilt = TiltAnalytics.analyze(features, Side.WHITE)
        assertEquals(0f, tilt.afterOneLoss, 0.01f)
        assertEquals(0f, tilt.afterTwoLosses, 0.01f)
        assertEquals(100f, tilt.afterThreePlusLosses, 0.01f)
        assertEquals(24, tilt.hourlyHeatmap.size)
        assertEquals(7, tilt.weekdayHeatmap.size)
    }

    @Test
    fun gameplanBuildsRecommendationsAndHeadToHead() {
        val games = listOf(
            testGame(id = "1", result = GameResult.WIN, white = "Opponent", black = "me"),
            testGame(id = "2", result = GameResult.LOSS, white = "Opponent", black = "me", eco = "C60"),
            testGame(id = "3", result = GameResult.LOSS, white = "Opponent", black = "me", eco = "C60"),
        )
        val card = OpponentGameplan.build(
            opponentName = "Opponent",
            platform = com.fredsystems.brilliantboard.domain.model.Platform.CHESS_COM,
            games = games,
            ownAccounts = setOf("me"),
        )
        assertEquals(3, card.gamesAnalyzed)
        assertEquals(3, card.headToHead.games)
        assertTrue(card.recommendations.isNotEmpty())
        assertTrue(card.worstAsBlack.isNotEmpty())
    }
}
