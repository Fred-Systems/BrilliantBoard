package com.fredsystems.brilliantboard.util

import android.content.Context
import java.io.File

/** Persists unexpected exceptions so failures can be diagnosed instead of vanishing. */
object CrashLogger {
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val dir = File(context.filesDir, "crash-logs")
                dir.mkdirs()
                val file = File(dir, "crash-${System.currentTimeMillis()}.txt")
                file.writeText(buildString {
                    append("Thread: ${thread.name}\n")
                    append("Time: ${System.currentTimeMillis()}\n")
                    append("Error: ${error.javaClass.name}: ${error.message}\n\n")
                    error.stackTrace.take(40).forEach { append("\t$it\n") }
                    append("\nCaused by:\n")
                    var cause = error.cause
                    var depth = 0
                    while (cause != null && depth < 5) {
                        append("${cause.javaClass.name}: ${cause.message}\n")
                        cause.stackTrace.take(20).forEach { append("\t$it\n") }
                        cause = cause.cause
                        depth++
                    }
                })
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun latest(context: Context): String? = runCatching {
        File(context.filesDir, "crash-logs")
            .listFiles()
            ?.filter { it.name.startsWith("crash-") }
            ?.maxByOrNull { it.lastModified() }
            ?.readText()
            ?.take(3000)
    }.getOrNull()
}
