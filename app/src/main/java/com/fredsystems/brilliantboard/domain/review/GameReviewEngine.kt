package com.fredsystems.brilliantboard.domain.review

import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.domain.model.EngineLine
import com.fredsystems.brilliantboard.domain.model.EvalPoint
import com.fredsystems.brilliantboard.domain.model.GameRecord
import com.fredsystems.brilliantboard.domain.model.GameReviewReport
import com.fredsystems.brilliantboard.domain.model.MoveBadge
import com.fredsystems.brilliantboard.domain.model.MoveReview
import com.fredsystems.brilliantboard.domain.model.Side
import com.fredsystems.brilliantboard.engine.PositionEvaluator

/** Produces a Chess.com style review from a sequence of engine evaluations. */
object GameReviewEngine {

    suspend fun analyse(
        game: GameRecord,
        evaluator: PositionEvaluator,
        depth: Int = 18,
        multiPv: Int = 3,
        maxPlies: Int = 200,
        onProgress: (Float) -> Unit = {},
    ): GameReviewReport {
        val initialFen = initialFen(game)
        val moves = game.moves.take(maxPlies)
        val uciHistory = mutableListOf<String>()
        val evaluations = mutableListOf<List<EngineLine>>()

        evaluations += evaluator.evaluate(initialFen, emptyList(), depth, multiPv)
        onProgress(0.02f)

        moves.forEachIndexed { index, move ->
            val uci = move.uci ?: move.san
            uciHistory += uci
            evaluations += evaluator.evaluate(initialFen, uciHistory, depth, multiPv)
            onProgress((index + 1).toFloat() / moves.size.coerceAtLeast(1) * 0.96f)
        }

        val bookSans = game.opening?.moves?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
        val reviews = mutableListOf<MoveReview>()
        val graph = mutableListOf<EvalPoint>()
        val whiteLosses = mutableListOf<Float>()
        val blackLosses = mutableListOf<Float>()

        var positionBefore = runCatching { Position.fromFen(initialFen) }.getOrDefault(Position.START)
        moves.forEachIndexed { index, move ->
            val beforeLines = evaluations[index]
            val afterLines = evaluations[index + 1]
            val moverIsWhite = move.side == Side.WHITE
            val bestBefore = beforeLines.firstOrNull()
            val bestAfter = afterLines.firstOrNull()
            val secondBest = beforeLines.getOrNull(1)

            val scoreBefore = bestBefore?.let { WinProbability.from(it.cp, it.mate) } ?: 50f
            val scoreAfterForMover = bestAfter?.let { WinProbability.from(it.cp, it.mate) }
                ?.let { if (moverIsWhite) 100f - it else it } ?: scoreBefore
            val secondBestWin = secondBest?.let { WinProbability.from(it.cp, it.mate) } ?: scoreAfterForMover

            val winBefore = if (moverIsWhite) scoreBefore else 100f - scoreBefore
            val winAfter = scoreAfterForMover
            val loss = (winBefore - winAfter).coerceAtLeast(0f)

            val badge = MoveClassifier.classify(
                ClassificationInput(
                    winBefore = winBefore,
                    winAfter = winAfter,
                    secondBestWinAfter = secondBestWin,
                    isInBook = index < bookSans.size && bookSans[index] == move.san,
                    isOnlyLegalMove = beforeLines.size == 1,
                    isSacrifice = move.isSacrifice,
                    isEngineTopMove = bestBefore?.moves?.firstOrNull()?.let {
                        it.equals(move.uci, true)
                    } == true,
                    missedForcedMate = bestBefore?.mate != null && bestAfter?.mate == null &&
                        bestBefore.mate != 0,
                )
            )

            if (moverIsWhite) whiteLosses += loss else blackLosses += loss

            val beforeWhiteWin = if (positionBefore.whiteToMove) scoreBefore else 100f - scoreBefore
            graph += EvalPoint(ply = move.ply, winPercent = beforeWhiteWin, cp = bestBefore?.cp, mate = bestBefore?.mate)

            reviews += MoveReview(
                ply = move.ply,
                san = move.san,
                badge = badge,
                winBefore = winBefore,
                winAfter = winAfter,
                winLoss = loss,
                bestMoveUci = bestBefore?.moves?.firstOrNull(),
                bestLine = bestBefore?.moves.orEmpty(),
                evaluationCp = bestBefore?.cp,
                mate = bestBefore?.mate,
                comment = MoveClassifier.comment(badge),
            )

            runCatching { Position.fromFen(move.fen) }.getOrNull()?.let { positionBefore = it }
        }

        onProgress(1f)
        val whiteAccuracy = AccuracyCalculator.gameAccuracy(whiteLosses)
        val blackAccuracy = AccuracyCalculator.gameAccuracy(blackLosses)
        return GameReviewReport(
            gameId = game.id,
            whiteAccuracy = whiteAccuracy,
            blackAccuracy = blackAccuracy,
            whitePerformanceElo = AccuracyCalculator.performanceRating(
                score = if (game.result.score == 1f) 1f else if (game.result.score == 0f) 0f else 0.5f,
                accuracy = whiteAccuracy,
                opponentRating = game.blackElo,
            ),
            blackPerformanceElo = AccuracyCalculator.performanceRating(
                score = 1f - game.result.score,
                accuracy = blackAccuracy,
                opponentRating = game.whiteElo,
            ),
            reviews = reviews,
            evalGraph = graph,
        )
    }

    private fun initialFen(game: GameRecord): String {
        val tagFen = game.pgn?.let { Regex("""\[FEN\s+"([^"]+)"\]""").find(it)?.groupValues?.get(1) }
        return tagFen?.takeIf { it != "-" } ?: Position.START.fen()
    }

    fun badgeFor(winLoss: Float): MoveBadge = MoveClassifier.classify(
        ClassificationInput(winBefore = 50f, winAfter = 50f - winLoss, secondBestWinAfter = 50f - winLoss)
    )
}
