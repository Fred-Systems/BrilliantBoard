package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.data.scraper.PgnParser
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.domain.model.Side
import com.fredsystems.brilliantboard.domain.scouting.TacticalMotifs
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalMotifsTest {
    @Test
    fun detectsAllowedMateInOne() {
        val pgn = """
            [White "Alice"]
            [Black "Bob"]
            [Result "0-1"]
            [ECO "A00"]

            1. f3 e5 2. g4 Qh4# 0-1
        """.trimIndent()
        val game = PgnParser.parse(pgn, Platform.CHESS_COM, user = "Alice")!!
        val profile = TacticalMotifs.analyze(listOf(game), Side.WHITE)
        assertTrue(profile.missedMateIn1 >= 1)
        assertTrue(profile.games == 1)
    }

    @Test
    fun cleanGameHasNoDetectedMotifs() {
        val profile = TacticalMotifs.analyze(emptyList(), Side.WHITE)
        assertTrue(profile.total == 0)
    }
}
