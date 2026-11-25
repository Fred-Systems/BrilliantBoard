package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.data.scraper.PgnParser
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.domain.model.Termination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnParserTest {
    private val pgn = """
        [Event "Test game"]
        [Site "https://lichess.org/abc123xyz"]
        [Date "2024.01.15"]
        [White "Alice"]
        [Black "Bob"]
        [WhiteElo "1800"]
        [BlackElo "1780"]
        [Result "1-0"]
        [ECO "B01"]
        [Opening "Scandinavian Defense"]
        [Termination "Time forfeit"]
        [TimeControl "300+5"]

        1. e4 {[%clk 0:04:58] [%eval 0.35]} d5 {[%clk 0:04:55] [%eval -0.12]} 2. exd5 {[%clk 0:04:50]} Qxd5 3. Nc3 1-0
    """.trimIndent()

    @Test
    fun parsesHeadersAndMoves() {
        val game = PgnParser.parse(pgn, Platform.LICHESS, user = "Alice")!!
        assertEquals("Alice", game.white)
        assertEquals("Bob", game.black)
        assertEquals(1800, game.whiteElo)
        assertEquals("B01", game.opening?.eco)
        assertEquals("Scandinavian Defense", game.opening?.name)
        assertEquals(5, game.moves.size)
        assertEquals("e4", game.moves.first().san)
        assertEquals("e2e4", game.moves.first().uci)
        assertTrue(game.moves.all { it.fen.count { c -> c.isLetter() } >= 12 })
    }

    @Test
    fun parsesClocksAndEvals() {
        val game = PgnParser.parse(pgn, Platform.LICHESS, user = "Alice")!!
        assertEquals(listOf(298, 290), game.clocks.whiteClocks)
        assertEquals(35, game.moves.first().evalCp)
        assertEquals(-12, game.moves[1].evalCp)
        assertEquals(300, game.clocks.initialSeconds)
        assertEquals(5, game.clocks.incrementSeconds)
    }

    @Test
    fun parsesTerminationAndResult() {
        val game = PgnParser.parse(pgn, Platform.LICHESS, user = "Alice")!!
        assertEquals(Termination.TIMEOUT, game.termination)
        assertEquals(com.fredsystems.brilliantboard.domain.model.GameResult.WIN, game.result)
        assertEquals(com.fredsystems.brilliantboard.domain.model.Side.WHITE, game.userSide)
    }

    @Test
    fun parsesClockStringsAndTimeControls() {
        assertEquals(323, PgnParser.clockToSeconds("0:05:23"))
        assertEquals(45, PgnParser.clockToSeconds("0:45"))
        assertEquals(Pair(180, 2), PgnParser.parseTimeControl("180+2"))
        assertEquals("bullet", PgnParser.timeClassFromControl("60+1"))
        assertEquals("daily", PgnParser.timeClassFromControl("1/86400"))
    }

    @Test
    fun rejectsGarbage() {
        assertEquals(null, PgnParser.parse("not a pgn at all", Platform.CHESS_COM))
    }

    @Test
    fun malformedMoveTokensAreSkippedWithoutCrashing() {
        val broken = """
            [White "A"][Black "B"][Result "*"][ECO "A00"]

            1. e4 zz e5 2. Nf3 1-0
        """.trimIndent()
        val game = PgnParser.parse(broken, Platform.CHESS_COM)
        assertNotNull(game)
        assertTrue(game!!.moves.isNotEmpty())
        assertTrue(game.moves.none { it.san == "zz" })
    }

    @Test
    fun detectsSacrificeAndCheck() {
        val matePgn = """
            [White "A"][Black "B"][Result "0-1"][ECO "A00"]

            1. f3 e5 2. g4 Qh4# 0-1
        """.trimIndent()
        val game = PgnParser.parse(matePgn, Platform.CHESS_COM)
        assertNotNull(game)
        assertTrue(game!!.moves.last().isCheck)
        assertTrue(game.moves.last().san.contains("#"))
    }
}
