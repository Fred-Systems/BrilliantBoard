package com.fredsystems.brilliantboard.data.repository

import com.fredsystems.brilliantboard.BuildConfig
import com.fredsystems.brilliantboard.data.remote.dto.GitHubAssetDto
import com.fredsystems.brilliantboard.data.remote.dto.GitHubReleaseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val name: String,
    val notes: String,
    val asset: GitHubAssetDto?,
    val url: String,
) {
    val isNewer: Boolean get() = VersionCompare.isNewer(version, BuildConfig.VERSION_NAME)
}

object VersionCompare {
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val length = maxOf(a.size, b.size)
        for (index in 0 until length) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}

/** Checks GitHub Releases and streams the signed APK for in-app update / save. */
class UpdateRepository(private val client: HttpClient) {
    companion object {
        private const val REPO = "Fred-Systems/BrilliantBoard"
        private const val API = "https://api.github.com/repos/$REPO/releases/latest"
        private const val USER_AGENT = "BrilliantBoard-Android/${BuildConfig.VERSION_NAME}"
    }

    suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val release: GitHubReleaseDto = client.get(API) {
                header("User-Agent", USER_AGENT)
                header("Accept", "application/vnd.github+json")
            }.body()
            val asset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@runCatching null
            ReleaseInfo(
                version = release.tagName,
                name = release.name ?: release.tagName,
                notes = release.body.orEmpty(),
                asset = asset,
                url = "https://github.com/$REPO/releases/tag/${release.tagName}",
            )
        }.getOrNull()
    }

    suspend fun download(
        url: String,
        target: File,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        var failure: Throwable? = null
        runCatching {
            val response = client.get(url) { header("User-Agent", USER_AGENT) }
            val channel = response.bodyAsChannel()
            val total = response.headers["Content-Length"]?.toLongOrNull() ?: 0L
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    written += read
                    if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }.onFailure { failure = it }

        if (!isValidApk(target)) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        val total = connection.contentLengthLong.coerceAtLeast(0)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                connection.disconnect()
            }.onFailure { failure = it }
        }

        if (!isValidApk(target)) {
            throw IllegalStateException(
                "Downloaded file is not a valid APK: ${failure?.message ?: "unknown error"}",
            )
        }
        onProgress(1f)
        target
    }

    private fun isValidApk(file: File): Boolean {
        if (!file.exists() || file.length() < 100_000L) return false
        return runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(2)
                input.read(magic, 0, 2) == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
            }
        }.getOrDefault(false)
    }
}
