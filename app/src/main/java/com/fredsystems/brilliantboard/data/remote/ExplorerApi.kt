package com.fredsystems.brilliantboard.data.remote

import com.fredsystems.brilliantboard.data.remote.dto.ExplorerMove
import com.fredsystems.brilliantboard.data.remote.dto.ExplorerResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json

data class MoveSuggestion(
    val san: String,
    val uci: String,
    val whitePercent: Float,
    val drawPercent: Float,
    val blackPercent: Float,
    val sampleSize: Int,
    val source: String,
)

class ExplorerApi(private val client: HttpClient, private val json: Json) {
    suspend fun explore(fen: String, source: String = "masters"): ExplorerResponse = runCatching {
        client.get("https://explorer.lichess.ovh/$source") {
            parameter("fen", fen)
            parameter("topGames", 0)
            parameter("recentGames", 0)
        }.body<ExplorerResponse>()
    }.getOrDefault(ExplorerResponse())

    suspend fun suggestions(fen: String, source: String = "masters"): List<MoveSuggestion> =
        explore(fen, source).moves.map { it.toSuggestion(source) }

    /** Blends explorer statistics with a local repertoire frequency overlay. */
    fun mergeWithRepertoire(
        suggestions: List<MoveSuggestion>,
        repertoire: Map<String, MoveFrequencyLocal>,
        weight: Float = 0.35f,
    ): List<MoveSuggestion> = suggestions.map { suggestion ->
        val local = repertoire[suggestion.uci] ?: repertoire[suggestion.san]
        if (local == null) suggestion else suggestion.copy(
            whitePercent = suggestion.whitePercent * (1 - weight) + local.winPercent * weight,
            sampleSize = suggestion.sampleSize + local.games,
        )
    }
}

data class MoveFrequencyLocal(val games: Int, val winPercent: Float)

private fun ExplorerMove.toSuggestion(source: String): MoveSuggestion {
    val total = (white + draw + black).coerceAtLeast(1)
    return MoveSuggestion(
        san = san,
        uci = uci,
        whitePercent = white * 100f / total,
        drawPercent = draw * 100f / total,
        blackPercent = black * 100f / total,
        sampleSize = total,
        source = source,
    )
}

@Suppress("unused")
private fun encodeFen(fen: String): String = fen.encodeURLParameter()
