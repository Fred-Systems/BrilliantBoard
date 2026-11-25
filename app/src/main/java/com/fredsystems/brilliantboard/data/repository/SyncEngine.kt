package com.fredsystems.brilliantboard.data.repository

import com.fredsystems.brilliantboard.data.local.AccountDao
import com.fredsystems.brilliantboard.data.local.GameDao
import com.fredsystems.brilliantboard.data.local.RatingDao
import com.fredsystems.brilliantboard.data.local.SyncStateDao
import com.fredsystems.brilliantboard.data.local.SyncStateEntity
import com.fredsystems.brilliantboard.data.remote.ChessComApi
import com.fredsystems.brilliantboard.data.remote.LichessApi
import com.fredsystems.brilliantboard.data.remote.dto.ChessComGame
import com.fredsystems.brilliantboard.data.scraper.PgnParser
import com.fredsystems.brilliantboard.domain.model.GameRecord
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.domain.model.PlayerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.Calendar

data class SyncResult(val gamesImported: Int, val platform: Platform, val username: String)

/** Imports and incrementally synchronises Chess.com and Lichess accounts. */
class SyncEngine(
    private val chessComApi: ChessComApi,
    private val lichessApi: LichessApi,
    private val parser: PgnParser,
    private val accountDao: AccountDao,
    private val gameDao: GameDao,
    private val ratingDao: RatingDao,
    private val syncStateDao: SyncStateDao,
) {
    suspend fun linkOwnAccount(username: String, platform: Platform) = withContext(Dispatchers.IO) {
        val profile = fetchProfile(username, platform) ?: return@withContext null
        accountDao.upsert(
            com.fredsystems.brilliantboard.data.local.AccountEntity(
                id = "${platform.name}:${profile.username.lowercase()}",
                username = profile.username.lowercase(),
                platform = platform.name,
                displayName = profile.displayName,
                avatarUrl = profile.avatarUrl,
                country = profile.country,
                isOwn = true,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        profile.copy(isOwnAccount = true)
    }

    suspend fun unlinkAccount(username: String, platform: Platform) = withContext(Dispatchers.IO) {
        accountDao.delete("${platform.name}:${username.lowercase()}")
    }

    suspend fun ownAccounts(): List<PlayerProfile> = withContext(Dispatchers.IO) {
        accountDao.ownAccounts().map { it.toProfile() }
    }

    suspend fun fetchProfile(username: String, platform: Platform): PlayerProfile? =
        withContext(Dispatchers.IO) {
            when (platform) {
                Platform.CHESS_COM -> chessComApi.profile(username)?.let { profile ->
                    profile.toProfile(platform, chessComApi.stats(username))
                }
                Platform.LICHESS -> lichessApi.profile(username)?.let { user ->
                    user.toProfile(lichessApi.ratingHistory(username))
                }
            }
        }

    suspend fun sync(username: String, platform: Platform): SyncResult = withContext(Dispatchers.IO) {
        val key = "${platform.name}:${username.lowercase()}"
        val state = syncStateDao.find(key)
        val lastSync = state?.lastSyncedAt ?: 0L
        val count = when (platform) {
            Platform.CHESS_COM -> syncChessCom(username, lastSync)
            Platform.LICHESS -> syncLichess(username, lastSync)
        }
        syncStateDao.upsert(
            state?.copy(lastSyncedAt = System.currentTimeMillis())
                ?: SyncStateEntity(key = key, lastSyncedAt = System.currentTimeMillis())
        )
        accountDao.upsert(
            com.fredsystems.brilliantboard.data.local.AccountEntity(
                id = key,
                username = username.lowercase(),
                platform = platform.name,
                isOwn = accountDao.find(username.lowercase(), platform.name)?.isOwn ?: false,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        SyncResult(count, platform, username)
    }

    private suspend fun syncChessCom(username: String, lastSync: Long): Int {
        val archives = chessComApi.archives(username)
        var imported = 0
        archives.filter { archiveEnd(it) >= lastSync }.forEach { url ->
            val games = chessComApi.monthlyGames(url)
            val records = games.mapNotNull { it.toRecord(username, url) }
            gameDao.upsertAll(records.map { it.toEntity(username.lowercase()) })
            imported += records.size
        }
        return imported
    }

    private suspend fun syncLichess(username: String, lastSync: Long): Int {
        lichessApi.profile(username)?.let { user ->
            val history = lichessApi.ratingHistory(username)
            ratingDao.deleteFor(username.lowercase(), Platform.LICHESS.name)
            ratingDao.upsertAll(history.map { (date, timeClass, rating) ->
                com.fredsystems.brilliantboard.domain.model.RatingPoint(
                    date, rating, com.fredsystems.brilliantboard.domain.model.TimeClass.from(timeClass), Platform.LICHESS
                ).toEntity(username, Platform.LICHESS)
            })
            accountDao.upsert(
                com.fredsystems.brilliantboard.data.local.AccountEntity(
                    id = "${Platform.LICHESS.name}:${username.lowercase()}",
                    username = username.lowercase(),
                    platform = Platform.LICHESS.name,
                    displayName = user.name,
                    country = user.country,
                    isOwn = accountDao.find(username.lowercase(), Platform.LICHESS.name)?.isOwn ?: false,
                )
            )
        }

        var imported = 0
        val batch = mutableListOf<GameRecord>()
        lichessApi.games(username, since = lastSync.takeIf { it > 0 }).collect { game ->
            val pgn = game.pgn ?: return@collect
            val record = parser.parse(pgn, Platform.LICHESS, user = username) ?: return@collect
            batch += record
            if (batch.size >= 250) {
                gameDao.upsertAll(batch.map { it.toEntity(username.lowercase()) })
                imported += batch.size
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            gameDao.upsertAll(batch.map { it.toEntity(username.lowercase()) })
            imported += batch.size
        }
        return imported
    }

    private fun ChessComGame.toRecord(username: String, archiveUrl: String): GameRecord? {
        val pgn = pgn ?: return null
        return parser.parse(pgn, Platform.CHESS_COM, user = username, fallbackId = url ?: "$archiveUrl:$endTime")
    }

    private fun archiveEnd(url: String): Long {
        val parts = url.trimEnd('/').split('/').takeLast(2)
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return Long.MAX_VALUE
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return Long.MAX_VALUE
        return Calendar.getInstance().apply { clear(); set(year, month, 1) }.timeInMillis
    }

    suspend fun scoutGames(username: String, platform: Platform): List<GameRecord> =
        withContext(Dispatchers.IO) {
            val cached = gameDao.observeGames(username.lowercase(), 10_000).firstOrNull().orEmpty()
            if (cached.isNotEmpty()) {
                cached.mapNotNull { it.toRecord(parser) }
            } else {
                sync(username, platform)
                gameDao.observeGames(username.lowercase(), 10_000).firstOrNull()
                    .orEmpty().mapNotNull { it.toRecord(parser) }
            }
        }
}
