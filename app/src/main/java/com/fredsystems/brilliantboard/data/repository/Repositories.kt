package com.fredsystems.brilliantboard.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fredsystems.brilliantboard.data.local.GameDao
import com.fredsystems.brilliantboard.data.scraper.PgnParser
import com.fredsystems.brilliantboard.domain.model.GameRecord
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.domain.model.Side
import com.fredsystems.brilliantboard.domain.model.TimeClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("brilliantboard_settings")

class ChessRepository(
    private val gameDao: GameDao,
    private val parser: PgnParser,
) {
    fun observeGames(username: String): Flow<List<GameRecord>> = gameDao
        .observeGames(username.lowercase(), 10_000)
        .map { entities -> entities.mapNotNull { it.toRecord(parser) } }

    suspend fun findGame(id: String): GameRecord? = gameDao.find(id)?.toRecord(parser)

    suspend fun filter(
        username: String,
        eco: String? = null,
        side: Side? = null,
        timeClass: TimeClass? = null,
        from: Long = 0L,
        to: Long = Long.MAX_VALUE,
    ): List<GameRecord> = gameDao.filter(
        username.lowercase(),
        eco,
        side?.name,
        timeClass?.takeIf { it != TimeClass.UNKNOWN }?.name,
        from,
        to,
    ).mapNotNull { it.toRecord(parser) }

    suspend fun count(username: String): Int = gameDao.count(username.lowercase())
}

data class AppSettings(
    val boardTheme: String = "emerald",
    val soundEnabled: Boolean = true,
    val analysisDepth: Int = 18,
    val multiPv: Int = 3,
    val dynamicColor: Boolean = false,
    val showLegalMoves: Boolean = true,
    val arrowsEnabled: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val BOARD_THEME = stringPreferencesKey("board_theme")
        val SOUND = booleanPreferencesKey("sound")
        val DEPTH = intPreferencesKey("depth")
        val MULTI_PV = intPreferencesKey("multipv")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val LEGAL = booleanPreferencesKey("show_legal")
        val ARROWS = booleanPreferencesKey("arrows")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            boardTheme = prefs[Keys.BOARD_THEME] ?: "emerald",
            soundEnabled = prefs[Keys.SOUND] != false,
            analysisDepth = prefs[Keys.DEPTH] ?: 18,
            multiPv = prefs[Keys.MULTI_PV] ?: 3,
            dynamicColor = prefs[Keys.DYNAMIC] == true,
            showLegalMoves = prefs[Keys.LEGAL] != false,
            arrowsEnabled = prefs[Keys.ARROWS] != false,
        )
    }

    suspend fun setBoardTheme(theme: String) = context.settingsDataStore.edit { it[Keys.BOARD_THEME] = theme }
    suspend fun setSound(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.SOUND] = enabled }
    suspend fun setDepth(depth: Int) = context.settingsDataStore.edit { it[Keys.DEPTH] = depth.coerceIn(12, 22) }
    suspend fun setMultiPv(count: Int) = context.settingsDataStore.edit { it[Keys.MULTI_PV] = count.coerceIn(1, 5) }
    suspend fun setDynamicColor(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.DYNAMIC] = enabled }
}

@Suppress("unused")
private val platformNames = listOf(Platform.CHESS_COM.name, Platform.LICHESS.name)
