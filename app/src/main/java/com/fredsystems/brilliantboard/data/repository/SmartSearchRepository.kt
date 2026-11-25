package com.fredsystems.brilliantboard.data.repository

import com.fredsystems.brilliantboard.data.local.AccountDao
import com.fredsystems.brilliantboard.data.local.GameDao
import com.fredsystems.brilliantboard.data.remote.ChessComApi
import com.fredsystems.brilliantboard.data.remote.LichessApi
import com.fredsystems.brilliantboard.domain.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PlayerSuggestion(
    val username: String,
    val platform: Platform,
    val source: String,
    val rating: Int? = null,
)

/** Debounced smart search: local history first, then exact remote profile matches. */
class SmartSearchRepository(
    private val accountDao: AccountDao,
    private val gameDao: GameDao,
    private val chessComApi: ChessComApi,
    private val lichessApi: LichessApi,
) {
    suspend fun suggest(query: String, owner: String? = null): List<PlayerSuggestion> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.length < 2) return@withContext emptyList()
            val pattern = "%$term%"
            val found = mutableListOf<PlayerSuggestion>()

            runCatching {
                accountDao.search("$term%").forEach { account ->
                    found += PlayerSuggestion(
                        account.username,
                        Platform.valueOf(account.platform),
                        if (account.isOwn) "My profile" else "Saved",
                    )
                }
            }
            if (owner != null) {
                runCatching {
                    (gameDao.whiteNames(pattern) + gameDao.blackNames(pattern)).forEach { name ->
                        found += PlayerSuggestion(name, Platform.CHESS_COM, "Game history")
                    }
                }
            }

            if (term.length >= 3) {
                runCatching {
                    chessComApi.profile(term)?.let { profile ->
                        found += PlayerSuggestion(profile.username, Platform.CHESS_COM, "Chess.com")
                    }
                }
                runCatching {
                    lichessApi.profile(term)?.let { profile ->
                        found += PlayerSuggestion(
                            profile.username,
                            Platform.LICHESS,
                            "Lichess",
                            profile.perfs?.values?.mapNotNull { it.rating }?.maxOrNull(),
                        )
                    }
                }
            }

            found
                .distinctBy { it.username.lowercase() to it.platform }
                .sortedBy { it.username.lowercase().indexOf(term.lowercase()) }
                .take(12)
        }
}
