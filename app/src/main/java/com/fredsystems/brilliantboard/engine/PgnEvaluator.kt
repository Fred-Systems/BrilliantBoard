package com.fredsystems.brilliantboard.engine

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
        val cp = move?.evalCp
        val mate = move?.mate
        if (cp == null && mate == null) return emptyList()
        return listOf(
            EngineLine(
                moves = listOfNotNull(move?.uci),
                cp = cp,
                mate = mate,
                depth = 0,
            )
        )
    }
}
