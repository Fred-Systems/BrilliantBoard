package com.fredsystems.brilliantboard.engine

import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.domain.model.EngineLine
import com.fredsystems.brilliantboard.domain.model.GameRecord

/**
 * Offline fallback evaluator that reuses cloud evaluations embedded in a PGN
 * (`[%eval ...]`, as provided by the Lichess games export).
 */
class PgnEvaluator(private val game: GameRecord) : PositionEvaluator {
    override suspend fun evaluate(
        fen: String,
        moves: List<String>,
        depth: Int,
        multiPv: Int,
    ): List<EngineLine> {
        val index = moves.size.coerceAtMost(game.moves.size)
        val move = game.moves.getOrNull(index)
        val cp = move?.evalCp ?: materialEstimate(fen)
        val mate = move?.mate
        return listOf(
            EngineLine(
                moves = listOfNotNull(move?.uci),
                cp = cp,
                mate = mate,
                depth = 0,
            )
        )
    }

    private fun materialEstimate(fen: String): Int {
        val position = runCatching { Position.fromFen(fen) }.getOrNull() ?: return 0
        val values = mapOf('p' to 100, 'n' to 320, 'b' to 330, 'r' to 500, 'q' to 900)
        var score = 0
        position.squares.forEach { piece ->
            if (piece == '.') return@forEach
            val value = values[piece.lowercaseChar()] ?: 0
            score += if (piece.isUpperCase()) value else -value
        }
        val oriented = if (position.whiteToMove) score else -score
        return oriented.coerceIn(-5000, 5000)
    }
}
