package com.fredsystems.brilliantboard.engine

import android.content.Context
import com.fredsystems.brilliantboard.domain.model.EngineLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.coroutines.cancellation.CancellationException

/**
 * Executes the bundled Stockfish binary through UCI. The native library is loaded from
 * nativeLibraryDir (extractNativeLibs=true), and all blocking IO stays on Dispatchers.Default.
 */
class StockfishEngine(private val context: Context) : PositionEvaluator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val analysisLock = kotlinx.coroutines.sync.Mutex()

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private val lines = Channel<String>(Channel.UNLIMITED)

    private val binaryPath: String? by lazy {
        val candidates = listOf(
            File(context.applicationInfo.nativeLibraryDir, "libstockfish.so"),
            File(context.filesDir, "stockfish/libstockfish.so"),
        )
        candidates.firstOrNull { it.exists() && it.canExecute() }?.absolutePath
    }

    val isAvailable: Boolean get() = binaryPath != null

    private fun start(): Boolean = synchronized(lock) {
        if (process != null) return true
        val path = binaryPath ?: return false
        return try {
            val builder = ProcessBuilder(path)
            builder.environment()["LD_LIBRARY_PATH"] = File(path).parent
            val spawned = builder.start()
            val input = BufferedReader(InputStreamReader(spawned.inputStream))
            val output = BufferedWriter(OutputStreamWriter(spawned.outputStream))
            spawned.errorStream.close()
            process = spawned
            writer = output
            Thread({
                try {
                    while (true) {
                        val line = input.readLine() ?: break
                        lines.trySend(line)
                    }
                } catch (_: Exception) {
                } finally {
                    lines.close()
                }
            }, "stockfish-reader").apply { isDaemon = true }.start()

            send("uci")
            val uciOk = waitForBlocking("uciok", 6_000)
            send("isready")
            val readyOk = waitForBlocking("readyok", 6_000)
            if (uciOk == null || readyOk == null) {
                process?.destroy()
                process = null
                writer = null
                return false
            }
            true
        } catch (_: Exception) {
            process?.destroy()
            process = null
            false
        }
    }

    private fun send(command: String) {
        val output = writer ?: return
        synchronized(lock) {
            output.write(command)
            output.newLine()
            output.flush()
        }
    }

    private fun waitForBlocking(token: String, timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val received = lines.tryReceive().getOrNull()
            if (received == null) {
                Thread.sleep(5)
            } else if (received.startsWith(token, ignoreCase = true)) {
                return received
            }
        }
        return null
    }

    fun setOptions(depth: Int, multiPv: Int) {
        if (!start()) return
        send("setoption name MultiPV value $multiPv")
        send("setoption name Threads value 1")
    }

    override suspend fun evaluate(
        fen: String,
        moves: List<String>,
        depth: Int,
        multiPv: Int,
    ): List<EngineLine> = withContext(Dispatchers.Default) {
        if (!start()) return@withContext emptyList()
        analysisLock.withLock {
            send("stop")
            drain()
            send("setoption name MultiPV value $multiPv")
            val moveSuffix = if (moves.isEmpty()) "" else " moves ${moves.joinToString(" ")}"
            send("position fen $fen$moveSuffix")
            send("go depth $depth")
            val best = mutableMapOf<Int, EngineLine>()
            runCatching {
                withTimeout(25_000L) {
                    while (true) {
                        val line = lines.receiveCatching().getOrNull() ?: break
                        if (line.startsWith("bestmove")) break
                        parseInfo(line)?.let { best[it.multiPv] = it.line }
                    }
                }
            }.onFailure { send("stop") }
            best.entries.sortedBy { it.key }.map { it.value }
        }
    }

    private fun drain() {
        while (lines.tryReceive().getOrNull() != null) { }
    }

    private fun parseInfo(line: String): RankedLine? {
        if (!line.startsWith("info ")) return null
        val depth = Regex("""\bdepth\s+(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val score = Regex("""\bscore\s+(cp|mate)\s+(-?\d+)""").find(line) ?: return null
        val pv = Regex("""\bpv\s+(.+)""").find(line)?.groupValues?.get(1)?.trim().orEmpty()
        val multiPv = Regex("""\bmultipv\s+(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val cp = if (score.groupValues[1] == "cp") score.groupValues[2].toIntOrNull() else null
        val mate = if (score.groupValues[1] == "mate") score.groupValues[2].toIntOrNull() else null
        return RankedLine(
            multiPv = multiPv,
            line = EngineLine(
                moves = pv.split(Regex("\\s+")).filter { it.isNotBlank() },
                cp = cp,
                mate = mate,
                depth = depth,
            ),
        )
    }

    private data class RankedLine(val multiPv: Int, val line: EngineLine)

    fun close() {
        synchronized(lock) {
            send("quit")
            process?.destroy()
            process = null
            writer = null
        }
    }
}

/** Abstraction over an evaluation source so the reviewer can be unit-tested offline. */
fun interface PositionEvaluator {
    suspend fun evaluate(
        fen: String,
        moves: List<String>,
        depth: Int,
        multiPv: Int,
    ): List<EngineLine>
}
