package com.fredsystems.brilliantboard.di

import androidx.room.Room
import com.fredsystems.brilliantboard.data.local.AppDatabase
import com.fredsystems.brilliantboard.data.remote.ChessComApi
import com.fredsystems.brilliantboard.data.remote.ETagCache
import com.fredsystems.brilliantboard.data.remote.ExplorerApi
import com.fredsystems.brilliantboard.data.remote.LichessApi
import com.fredsystems.brilliantboard.data.remote.RateLimiter
import com.fredsystems.brilliantboard.data.repository.ChessRepository
import com.fredsystems.brilliantboard.data.repository.SettingsRepository
import com.fredsystems.brilliantboard.data.repository.SyncEngine
import com.fredsystems.brilliantboard.data.scraper.PgnParser
import com.fredsystems.brilliantboard.engine.StockfishEngine
import com.fredsystems.brilliantboard.ui.HomeViewModel
import com.fredsystems.brilliantboard.ui.MyProfileViewModel
import com.fredsystems.brilliantboard.ui.ReviewViewModel
import com.fredsystems.brilliantboard.ui.SandboxViewModel
import com.fredsystems.brilliantboard.ui.ScoutViewModel
import com.fredsystems.brilliantboard.ui.SettingsViewModel
import com.fredsystems.brilliantboard.util.MoveSounds
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.time.Duration
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }

    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(get()) }
            install(Logging) { level = LogLevel.NONE }
            engine {
                config {
                    connectTimeout(Duration.ofSeconds(15))
                    readTimeout(Duration.ofSeconds(30))
                }
            }
        }
    }

    single { RateLimiter(300L) }
    single { ETagCache() }
    single { ChessComApi(get(), get(), get()) }
    single { LichessApi(get(), get(), get()) }
    single { ExplorerApi(get(), get()) }
    single { PgnParser }

    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "brilliantboard.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single { get<AppDatabase>().accountDao() }
    single { get<AppDatabase>().gameDao() }
    single { get<AppDatabase>().ratingDao() }
    single { get<AppDatabase>().syncStateDao() }

    single { ChessRepository(get(), get()) }
    single { SettingsRepository(androidContext()) }
    single { SyncEngine(get(), get(), get(), get(), get(), get(), get()) }
    single { StockfishEngine(androidContext()) }
    single { MoveSounds(androidContext()) }

    viewModel { HomeViewModel(get(), get()) }
    viewModel { MyProfileViewModel(get(), get()) }
    viewModel { ScoutViewModel(get(), get(), get()) }
    viewModel { ReviewViewModel(get(), get()) }
    viewModel { SandboxViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
}
