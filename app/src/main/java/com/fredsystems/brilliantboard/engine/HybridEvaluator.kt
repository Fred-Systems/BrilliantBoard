package com.fredsystems.brilliantboard.engine

import com.fredsystems.brilliantboard.domain.model.EngineLine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Uses the real engine when it answers, otherwise falls back to PGN/material estimates so
 * review never stalls on a broken or missing binary.
 */
class HybridEvaluator(
    private val primary: PositionEvaluator,
    private val fallback: PositionEvaluator,
    private val timeoutMs: Long = 12_000L,
) : PositionEvaluator {
    override suspend fun evaluate(
        fen: String,
        moves: List<String>,
        depth: Int,
        multiPv: Int,
    ): List<EngineLine> {
        val result = withTimeoutOrNull(timeoutMs) {
            runCatching { primary.evaluate(fen, moves, depth, multiPv) }.getOrDefault(emptyList())
        }.orEmpty()
        return result.ifEmpty {
            withTimeout(timeoutMs) { fallback.evaluate(fen, moves, depth, multiPv) }
        }
    }
}
