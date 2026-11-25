package com.fredsystems.brilliantboard.data.remote

import com.fredsystems.brilliantboard.data.remote.dto.ChessComProfile
import com.fredsystems.brilliantboard.data.remote.dto.ChessComStats
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Polite 300ms spacing between calls to a public API. */
class RateLimiter(private val delayMillis: Long = 300L) {
    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun waitTurn() = mutex.withLock {
        val now = System.currentTimeMillis()
        val remaining = delayMillis - (now - lastRequestAt)
        if (remaining > 0) kotlinx.coroutines.delay(remaining)
        lastRequestAt = System.currentTimeMillis()
    }
}

/** Reusable ETag conditional-request cache. */
class ETagCache {
    private val tags = mutableMapOf<String, String>()

    fun etagFor(url: String): String? = tags[url]

    fun remember(response: HttpResponse, url: String) {
        response.headers["ETag"]?.let { tags[url] = it }
    }
}

class ChessComApi(
    private val client: HttpClient,
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val etagCache: ETagCache = ETagCache(),
) {
    companion object {
        const val USER_AGENT = "BrilliantBoard-Android/1.0 (https://github.com/Fred-Systems/BrilliantBoard)"
        private const val BASE = "https://api.chess.com/pub"
    }

    private suspend inline fun <reified T> fetch(url: String): T? = withContext(Dispatchers.IO) {
        rateLimiter.waitTurn()
        val response: HttpResponse = client.request(url) {
            header("User-Agent", USER_AGENT)
            header("Accept", "application/json")
            etagCache.etagFor(url)?.let { header("If-None-Match", it) }
        }
        etagCache.remember(response, url)
        when (response.status) {
            HttpStatusCode.NotModified -> null
            HttpStatusCode.NotFound -> null
            else -> {
                if (response.status.value !in 200..299) error("Chess.com request failed (${response.status}): $url")
                response.body<T>()
            }
        }
    }

    suspend fun profile(username: String): ChessComProfile? =
        fetch<ChessComProfile>("$BASE/player/${username.trim().lowercase()}")

    suspend fun stats(username: String): ChessComStats? =
        fetch<ChessComStats>("$BASE/player/${username.trim().lowercase()}/stats")

    suspend fun archives(username: String): List<String> =
        fetch<com.fredsystems.brilliantboard.data.remote.dto.ChessComArchives>(
            "$BASE/player/${username.trim().lowercase()}/games/archives"
        )?.archives.orEmpty()

    suspend fun monthlyGames(archiveUrl: String): List<com.fredsystems.brilliantboard.data.remote.dto.ChessComGame> =
        fetch<com.fredsystems.brilliantboard.data.remote.dto.ChessComGamePage>(archiveUrl)?.games.orEmpty()
}
