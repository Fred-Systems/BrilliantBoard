package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.domain.model.MoveBadge
import com.fredsystems.brilliantboard.domain.review.AccuracyCalculator
import com.fredsystems.brilliantboard.domain.review.ClassificationInput
import com.fredsystems.brilliantboard.domain.review.MoveClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveClassifierTest {
    private fun input(
        before: Float,
        after: Float,
        second: Float = after,
        block: ClassificationInput.() -> ClassificationInput = { this },
    ) = ClassificationInput(before, after, second).block()

    @Test
    fun bookAndForcedTakePriority() {
        assertEquals(MoveBadge.BOOK, MoveClassifier.classify(input(50f, 50f) { copy(isInBook = true) }))
        assertEquals(MoveBadge.FORCED, MoveClassifier.classify(input(50f, 10f) { copy(isOnlyLegalMove = true) }))
    }

    @Test
    fun classifiesByWinLossBands() {
        assertEquals(MoveBadge.BEST, MoveClassifier.classify(input(60f, 59.5f)))
        assertEquals(MoveBadge.GOOD, MoveClassifier.classify(input(60f, 57f)))
        assertEquals(MoveBadge.INACCURACY, MoveClassifier.classify(input(60f, 53f)))
        assertEquals(MoveBadge.MISTAKE, MoveClassifier.classify(input(60f, 45f)))
        assertEquals(MoveBadge.BLUNDER, MoveClassifier.classify(input(60f, 35f)))
    }

    @Test
    fun brilliantRequiresSoundSacrifice() {
        val input = input(50f, 55f, 40f) { copy(isSacrifice = true) }
        assertEquals(MoveBadge.BRILLIANT, MoveClassifier.classify(input))
        assertEquals(MoveBadge.BEST, MoveClassifier.classify(input.copy(isSacrifice = false, secondBestWinAfter = 55f)))
    }

    @Test
    fun greatMoveRequiresLargeSecondBestDrop() {
        val input = input(60f, 60f, 45f)
        assertEquals(MoveBadge.GREAT, MoveClassifier.classify(input))
    }

    @Test
    fun missedMateGetsItsOwnBadge() {
        val input = input(95f, 70f) { copy(missedForcedMate = true) }
        assertEquals(MoveBadge.MISSED_MATE, MoveClassifier.classify(input))
    }
}

class AccuracyCalculatorTest {
    @Test
    fun perfectMoveIsHundredPercent() {
        assertEquals(100f, AccuracyCalculator.moveAccuracy(0f), 0.001f)
    }

    @Test
    fun accuracyDeclinesWithLoss() {
        assertTrue(AccuracyCalculator.moveAccuracy(3f) > AccuracyCalculator.moveAccuracy(12f))
        assertTrue(AccuracyCalculator.moveAccuracy(12f) > AccuracyCalculator.moveAccuracy(30f))
    }

    @Test
    fun gameAccuracyAveragesMoves() {
        assertEquals(100f, AccuracyCalculator.gameAccuracy(listOf(0f, 0f, 0f)), 0.001f)
    }

    @Test
    fun performanceRatingBeatsOpponentAfterGoodGame() {
        val rating = AccuracyCalculator.performanceRating(1f, 85f, 1800)
        assertTrue(rating > 1800)
    }

    @Test
    fun performanceRatingIsClamped() {
        val rating = AccuracyCalculator.performanceRating(0f, 0f, 1000)
        assertTrue(rating >= 100)
    }
}
