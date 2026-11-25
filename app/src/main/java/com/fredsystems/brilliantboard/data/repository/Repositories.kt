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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("brilliantboard_settings")

class ChessRepository(
    private val gameDao: GameDao,
    private val parser: PgnParser,
) {
    fun observeGames(username: String): Flow<List<GameRecord>> = gameDao
        .observeGames(username.lowercase(), 10_000)
        .map { entities ->
            entities.mapNotNull { entity ->
                runCatching { entity.toRecord(parser) }.getOrNull()
            }
        }
        .flowOn(Dispatchers.Default)

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
    val boardTheme: String = "brown",
    val pieceSet: String = "cburnett",
    val soundEnabled: Boolean = true,
    val analysisDepth: Int = 18,
    val multiPv: Int = 3,
    val dynamicColor: Boolean = false,
    val showLegalMoves: Boolean = true,
    val arrowsEnabled: Boolean = true,
    val boardScale: Float = 1f,
    val autoCheckUpdates: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val BOARD_THEME = stringPreferencesKey("board_theme")
        val PIECE_SET = stringPreferencesKey("piece_set")
        val SOUND = booleanPreferencesKey("sound")
        val DEPTH = intPreferencesKey("depth")
        val MULTI_PV = intPreferencesKey("multipv")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val LEGAL = booleanPreferencesKey("show_legal")
        val ARROWS = booleanPreferencesKey("arrows")
        val BOARD_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("board_scale")
        val AUTO_UPDATES = booleanPreferencesKey("auto_updates")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            boardTheme = prefs[Keys.BOARD_THEME] ?: "brown",
            pieceSet = prefs[Keys.PIECE_SET] ?: "cburnett",
            soundEnabled = prefs[Keys.SOUND] != false,
            analysisDepth = prefs[Keys.DEPTH] ?: 18,
            multiPv = prefs[Keys.MULTI_PV] ?: 3,
            dynamicColor = prefs[Keys.DYNAMIC] == true,
            showLegalMoves = prefs[Keys.LEGAL] != false,
            arrowsEnabled = prefs[Keys.ARROWS] != false,
            boardScale = prefs[Keys.BOARD_SCALE] ?: 1f,
            autoCheckUpdates = prefs[Keys.AUTO_UPDATES] != false,
        )
    }

    suspend fun setBoardTheme(theme: String) = context.settingsDataStore.edit { it[Keys.BOARD_THEME] = theme }
    suspend fun setPieceSet(set: String) = context.settingsDataStore.edit { it[Keys.PIECE_SET] = set }
    suspend fun setBoardScale(scale: Float) = context.settingsDataStore.edit {
        it[Keys.BOARD_SCALE] = scale.coerceIn(0.6f, 1.5f)
    }
    suspend fun setAutoUpdates(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.AUTO_UPDATES] = enabled }
    suspend fun setSound(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.SOUND] = enabled }
    suspend fun setDepth(depth: Int) = context.settingsDataStore.edit { it[Keys.DEPTH] = depth.coerceIn(12, 22) }
    suspend fun setMultiPv(count: Int) = context.settingsDataStore.edit { it[Keys.MULTI_PV] = count.coerceIn(1, 5) }
    suspend fun setDynamicColor(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.DYNAMIC] = enabled }
}

@Suppress("unused")
private val platformNames = listOf(Platform.CHESS_COM.name, Platform.LICHESS.name)
