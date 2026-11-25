package com.fredsystems.brilliantboard.domain.review

import kotlin.math.exp

/**
 * Chess.com style centipawn -> win probability conversion.
 *
 * win% = 50 + 50 * (2 / (1 + exp(-0.00368208 * cp)) - 1)
 *      = 100 / (1 + exp(-0.00368208 * cp))
 */
object WinProbability {
    const val K: Double = 0.00368208

    fun fromCentipawns(cp: Int): Float {
        val exponent = (-K * cp.coerceIn(-100_000, 100_000))
        return (100.0 / (1.0 + exp(exponent))).toFloat()
    }

    /** Converts a mate score into an almost-certain win/loss probability. */
    fun fromMate(mate: Int): Float = if (mate > 0) {
        99.9f - (mate - 1).coerceAtMost(8) * 0.05f
    } else {
        0.1f + (-mate - 1).coerceAtMost(8) * 0.05f
    }

    fun from(cp: Int?, mate: Int?): Float? = when {
        mate != null -> fromMate(mate)
        cp != null -> fromCentipawns(cp)
        else -> null
    }

    /**
     * Engine scores are relative to the side to move. This flips a score so that it is
     * expressed from [perspective] point of view.
     */
    fun forSide(cp: Int?, mate: Int?, whiteToMove: Boolean, perspectiveIsWhite: Boolean): Float? {
        val score = from(cp, mate) ?: return null
        val flip = whiteToMove != perspectiveIsWhite
        return if (flip) 100f - score else score
    }

    fun centipawnsFor(winPercent: Float): Int {
        val p = winPercent.coerceIn(0.01f, 99.99f) / 100.0
        val odds = p / (1.0 - p)
        return (kotlin.math.ln(odds) / K).toInt()
    }
}
