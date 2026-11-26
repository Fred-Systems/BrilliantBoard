package com.fredsystems.brilliantboard.engine

import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.domain.model.EngineLine

/** Deterministic material-based evaluation used when no engine or PGN eval is available. */
object MaterialEvaluator : PositionEvaluator {
    override suspend fun evaluate(
        fen: String,
        moves: List<String>,
        depth: Int,
        multiPv: Int,
    ): List<EngineLine> = listOf(EngineLine(moves = emptyList(), cp = estimate(fen), depth = 0))

    fun estimate(fen: String): Int {
        val position = runCatching { Position.fromFen(fen) }.getOrNull() ?: return 0
        val values = mapOf('p' to 100, 'n' to 320, 'b' to 330, 'r' to 500, 'q' to 900)
        var score = 0
        position.squares.forEach { piece ->
            if (piece == '.') return@forEach
            val value = values[piece.lowercaseChar()] ?: 0
            score += if (piece.isUpperCase()) value else -value
        }
        return (if (position.whiteToMove) score else -score).coerceIn(-5000, 5000)
    }
}
