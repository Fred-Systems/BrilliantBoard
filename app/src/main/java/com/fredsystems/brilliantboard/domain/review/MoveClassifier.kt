package com.fredsystems.brilliantboard.domain.review

import com.fredsystems.brilliantboard.domain.model.MoveBadge

/**
 * Parameters describing a single move from the mover's point of view. All win values are
 * percentages in 0..100 and already flipped so that 100 means "mover is winning".
 */
data class ClassificationInput(
    val winBefore: Float,
    val winAfter: Float,
    val secondBestWinAfter: Float,
    val isInBook: Boolean = false,
    val isOnlyLegalMove: Boolean = false,
    val isSacrifice: Boolean = false,
    val isEngineTopMove: Boolean = true,
    val missedForcedMate: Boolean = false,
    val isPromotion: Boolean = false,
)

object MoveClassifier {
    fun classify(input: ClassificationInput): MoveBadge {
        if (input.isInBook) return MoveBadge.BOOK
        if (input.isOnlyLegalMove) return MoveBadge.FORCED

        val loss = (input.winBefore - input.winAfter).coerceAtLeast(0f)

        if (input.isSacrifice && input.winAfter >= 50f && input.winBefore < 92f &&
            loss <= 10f && !input.isOnlyLegalMove
        ) {
            return MoveBadge.BRILLIANT
        }

        val secondBestDrop = input.winAfter - input.secondBestWinAfter
        if (secondBestDrop >= 12f && loss <= 5f && input.winAfter >= 50f &&
            input.isEngineTopMove
        ) {
            return MoveBadge.GREAT
        }

        if (input.missedForcedMate && loss > 5f) return MoveBadge.BLUNDER

        return when {
            loss <= 1f && input.isEngineTopMove -> MoveBadge.BEST
            loss <= 5f -> MoveBadge.GOOD
            loss <= 10f -> MoveBadge.INACCURACY
            loss <= 20f -> MoveBadge.MISTAKE
            else -> MoveBadge.BLUNDER
        }
    }

    fun comment(badge: MoveBadge): String = when (badge) {
        MoveBadge.BOOK -> "Opening book move"
        MoveBadge.FORCED -> "Only legal move"
        MoveBadge.BEST -> "Best move"
        MoveBadge.GREAT -> "Great move! Only move keeping the advantage"
        MoveBadge.BRILLIANT -> "Brilliant sacrifice!"
        MoveBadge.GOOD -> "Good move"
        MoveBadge.INACCURACY -> "Slight inaccuracy"
        MoveBadge.MISTAKE -> "Mistake"
        MoveBadge.BLUNDER -> "Blunder"
    }
}
