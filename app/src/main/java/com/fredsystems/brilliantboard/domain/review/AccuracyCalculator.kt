package com.fredsystems.brilliantboard.domain.review

import kotlin.math.exp
import kotlin.math.roundToInt

/** Converts per-move win-rate loss into an overall accuracy score and performance rating. */
object AccuracyCalculator {

    /** Accuracy contribution of a single move, where the input is win% lost by that move. */
    fun moveAccuracy(winLoss: Float): Float {
        val loss = winLoss.coerceAtLeast(0f).toDouble()
        return (100.0 * exp(-loss / 20.0)).toFloat().coerceIn(0f, 100f)
    }

    fun gameAccuracy(moveLosses: List<Float>): Float {
        if (moveLosses.isEmpty()) return 0f
        return (moveLosses.sumOf { moveAccuracy(it).toDouble() } / moveLosses.size).toFloat()
    }

    /**
     * Performance rating approximation: result strength plus an accuracy adjustment.
     * Opponent rating defaults to 1500 when unavailable.
     */
    fun performanceRating(
        score: Float,
        accuracy: Float,
        opponentRating: Int? = null,
        games: Int = 1,
    ): Int {
        val base = (opponentRating ?: 1500).toDouble()
        val resultComponent = (2.0 * score.coerceIn(0f, 1f) - 1.0) * 200.0
        val accuracyComponent = (accuracy.coerceIn(0f, 100f) - 65.0) * 4.0
        val reliability = (games.coerceAtLeast(1).toDouble().sqrt()).coerceAtMost(3.0)
        return (base + (resultComponent + accuracyComponent) / reliability).roundToInt().coerceIn(100, 3500)
    }

    private fun Double.sqrt(): Double = kotlin.math.sqrt(this)
}
