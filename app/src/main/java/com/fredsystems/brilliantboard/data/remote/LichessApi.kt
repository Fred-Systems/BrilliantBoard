package com.fredsystems.brilliantboard.data.remote

import com.fredsystems.brilliantboard.data.remote.dto.LichessGame
import com.fredsystems.brilliantboard.data.remote.dto.LichessUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json

class LichessApi(
    private val client: HttpClient,
    private val json: Json,
    private val rateLimiter: RateLimiter = RateLimiter(),
) {
    companion object {
        const val USER_AGENT = "BrilliantBoard-Android/1.0 (https://github.com/Fred-Systems/BrilliantBoard)"
        private const val BASE = "https://lichess.org/api"
    }

    suspend fun profile(username: String): LichessUser? = runCatching {
        rateLimiter.waitTurn()
        val response = client.get("$BASE/user/${username.trim()}") {
            header("User-Agent", USER_AGENT)
            header("Accept", "application/json")
        }
        if (response.status == HttpStatusCode.NotFound) null else response.body<LichessUser>()
    }.getOrNull()

    /**
     * Lichess rating history is an array of arrays: [year, month, bullet, blitz, rapid, daily].
     * This returns a plain list of (timestamp, timeClass, rating) triples.
     */
    suspend fun ratingHistory(username: String): List<Triple<Long, String, Int>> = runCatching {
        rateLimiter.waitTurn()
        val response = client.get("$BASE/user/${username.trim()}/rating-history") {
            header("User-Agent", USER_AGENT)
        }
        val rows = json.parseToJsonElement(response.body<String>()).toString()
        val trimmed = rows.trim().removePrefix("[").removeSuffix("]")
        val result = mutableListOf<Triple<Long, String, Int>>()
        val rowRegex = Regex("""\[(\d{4}),(\d{1,2}),(-?\d+),(-?\d+),(-?\d+),(-?\d+)\]""")
        rowRegex.findAll(trimmed).forEach { match ->
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val timestamp = java.util.Calendar.getInstance().apply {
                clear(); set(year, month - 1, 1)
            }.timeInMillis
            val classes = listOf("bullet", "blitz", "rapid", "daily")
            for (index in 3..6) {
                val rating = match.groupValues[index].toIntOrNull() ?: continue
                if (rating > 0) result += Triple(timestamp, classes[index - 3], rating)
            }
        }
        result
    }.getOrDefault(emptyList())

    suspend fun activity(username: String): String = runCatching {
        rateLimiter.waitTurn()
        client.get("$BASE/user/${username.trim()}/activity") {
            header("User-Agent", USER_AGENT)
        }.body<String>()
    }.getOrDefault("")

    /**
     * Streams the games export as NDJSON so a 10,000+ game history never has to be held
     * in memory at once. Reuses cloud evals embedded in the PGN comments.
     */
    fun games(username: String, since: Long? = null): Flow<LichessGame> = flow {
        rateLimiter.waitTurn()
        val url = buildString {
            append("$BASE/games/user/${username.trim()}?pgnInJson=true&clocks=true&evals=true&opening=true&sort=desc")
            since?.let { append("&since=$it") }
        }
        val response = client.get(url) {
            header("User-Agent", USER_AGENT)
            header("Accept", "application/x-ndjson")
        }
        if (response.status != HttpStatusCode.OK) return@flow
        val channel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.isBlank()) continue
            runCatching { json.decodeFromString<LichessGame>(line) }.getOrNull()?.let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)
}
