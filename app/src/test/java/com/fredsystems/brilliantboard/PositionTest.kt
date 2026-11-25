package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.domain.chess.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionTest {
    @Test
    fun startPositionFenRoundTrips() {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        assertEquals(fen, Position.START.fen())
        assertEquals(fen, Position.fromFen(fen).fen())
    }

    @Test
    fun startPositionHasTwentyLegalMoves() {
        assertEquals(20, Position.START.legalMoves().size)
    }

    @Test
    fun parsesAndAppliesSan() {
        val e4 = Position.START.parseSan("e4")
        assertNotNull(e4)
        assertEquals("e2e4", e4!!.uci)
        assertEquals("e4", Position.START.san(e4))
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            Position.START.apply(e4).fen(),
        )
    }

    @Test
    fun detectsFoolsMate() {
        var position = Position.START
        listOf("f3", "e5", "g4", "Qh4#").forEach { san ->
            val move = position.parseSan(san)
            assertNotNull("Could not parse $san", move)
            position = position.apply(move!!)
        }
        assertTrue(position.isCheck())
        assertTrue(position.isCheckmate())
        assertTrue(position.legalMoves().isEmpty())
    }

    @Test
    fun castlesBothSides() {
        val position = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val short = position.parseSan("O-O")
        assertNotNull(short)
        assertEquals("r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1", position.apply(short!!).fen())
        val long = position.parseSan("O-O-O")
        assertNotNull(long)
        val applied = position.apply(long!!)
        assertEquals('R', applied.pieceAt(3))
        assertEquals('K', applied.pieceAt(2))
    }

    @Test
    fun capturesEnPassant() {
        val position = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2")
        val move = position.parseUci("e5d6")
        assertNotNull(move)
        assertTrue(position.isLegal(move!!))
        val after = position.apply(move)
        assertEquals('.', after.pieceAt(Position.squareIndex("d5")!!))
        assertEquals('P', after.pieceAt(Position.squareIndex("d6")!!))
    }

    @Test
    fun promotesPawn() {
        val position = Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        val move = position.parseUci("a7a8q")
        assertNotNull(move)
        assertEquals('Q', position.apply(move!!).pieceAt(56))
    }

    @Test
    fun shortMalformedUciNeverThrows() {
        assertEquals(null, Position.START.parseUci("e4"))
        assertEquals(null, Position.START.parseUci("e2e"))
        assertEquals(null, Position.START.parseUci(""))
    }

    @Test
    fun disambiguatesKnightMoves() {
        val position = Position.fromFen("4k3/8/8/8/8/8/8/4K2N w - - 0 1")
        val legalMoves = position.legalMoves()
        assertTrue(legalMoves.isNotEmpty())
        assertFalse(position.isCheck())
    }
}
