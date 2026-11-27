package com.fredsystems.brilliantboard

import android.app.Application
import com.fredsystems.brilliantboard.di.appModule
import com.fredsystems.brilliantboard.util.CrashLogger
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BrilliantBoardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        startKoin {
            androidContext(this@BrilliantBoardApp)
            modules(appModule)
        }
    }
}
