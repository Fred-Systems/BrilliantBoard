package com.fredsystems.brilliantboard.domain.scouting

import com.fredsystems.brilliantboard.domain.chess.ChessMove
import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.domain.model.GameRecord
import com.fredsystems.brilliantboard.domain.model.Side

data class TacticalProfile(
    val missedMateIn1: Int = 0,
    val forks: Int = 0,
    val pins: Int = 0,
    val hangingPieces: Int = 0,
    val backRankWeaknesses: Int = 0,
    val games: Int = 0,
) {
    val total: Int get() = missedMateIn1 + forks + pins + hangingPieces + backRankWeaknesses
}

/** Detects recurring tactical motifs directly from stored positions — no engine required. */
object TacticalMotifs {

    fun analyze(games: List<GameRecord>, side: Side): TacticalProfile {
        var missedMate = 0
        var forks = 0
        var pins = 0
        var hanging = 0
        var backRank = 0

        games.forEach { game ->
            game.moves.forEachIndexed { index, move ->
                if (move.side != side) return@forEachIndexed
                val before = runCatching {
                    game.moves.getOrNull(index - 1)?.fen?.let { Position.fromFen(it) } ?: Position.START
                }.getOrNull() ?: return@forEachIndexed
                val after = runCatching { Position.fromFen(move.fen) }.getOrNull() ?: return@forEachIndexed

                val ownMobility = attackedSquares(after, side == Side.WHITE)
                val enemyMobility = attackedSquares(after, side != Side.WHITE)

                val valuableTargets = after.squares.mapIndexedNotNull { square, piece ->
                    if (piece != '.' && piece.isUpperCase() != (side == Side.WHITE) &&
                        piece.lowercaseChar() in listOf('q', 'r', 'k')
                    ) square else null
                }
                if (valuableTargets.count { it in ownMobility } >= 2) forks++

                val pinned = after.squares.indices.count { square ->
                    val piece = after.pieceAt(square)
                    if (piece == '.' || piece.isUpperCase() != (side == Side.WHITE)) return@count false
                    val without = after.copy(squares = after.squares.replaceRange(square, square + 1, "."))
                    val king = if (side == Side.WHITE) 'K' else 'k'
                    val kingIndex = without.squares.indexOf(king)
                    kingIndex >= 0 && without.isAttacked(kingIndex, side != Side.WHITE)
                }
                if (pinned > 0) pins++

                val enemyCanWinMaterial = after.legalMoves().any { enemyMove: ChessMove ->
                    val captured = after.pieceAt(enemyMove.to)
                    captured != '.' && captured.isUpperCase() == (side == Side.WHITE) &&
                        pieceValue(captured) >= 300 && enemyMove.to !in ownMobility
                }
                if (enemyCanWinMaterial) hanging++

                if (opponentHasMateInOne(after) && !givesMate(before, move)) missedMate++

                val enemyKing = if (side == Side.WHITE) 'k' else 'K'
                val kingSquare = after.squares.indexOf(enemyKing)
                if (kingSquare >= 0 && (kingSquare / 8 == 0 || kingSquare / 8 == 7) &&
                    enemyMobility.isEmpty()
                ) backRank++
            }
        }

        return TacticalProfile(missedMate, forks, pins, hanging, backRank, games.size)
    }

    private fun givesMate(before: Position, move: com.fredsystems.brilliantboard.domain.model.MoveRecord): Boolean {
        val parsed = move.uci?.let { before.parseUci(it) } ?: before.parseSan(move.san) ?: return false
        if (!before.isLegal(parsed)) return false
        return before.apply(parsed).isCheckmate()
    }

    private fun opponentHasMateInOne(position: Position): Boolean =
        position.legalMoves().any { move -> position.apply(move).isCheckmate() }

    private fun attackedSquares(position: Position, byWhite: Boolean): Set<Int> =
        position.pseudoLegalMoves()
            .filter { move -> position.pieceAt(move.from).isUpperCase() == byWhite }
            .mapTo(mutableSetOf()) { it.to }

    private fun pieceValue(piece: Char): Int = when (piece.lowercaseChar()) {
        'p' -> 100; 'n' -> 320; 'b' -> 330; 'r' -> 500; 'q' -> 900; else -> 0
    }
}
