package com.fredsystems.brilliantboard.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fredsystems.brilliantboard.data.repository.ReleaseInfo
import com.fredsystems.brilliantboard.data.repository.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UpdateState(
    val checking: Boolean = false,
    val info: ReleaseInfo? = null,
    val error: String? = null,
    val progress: Float = 0f,
    val downloading: Boolean = false,
    val status: String? = null,
)

class UpdateViewModel(private val repository: UpdateRepository) : ViewModel() {
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun check(manual: Boolean = true) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { repository.latest() }
            val info = result.getOrNull()
            _state.update {
                it.copy(
                    checking = false,
                    info = info,
                    error = when {
                        result.isFailure -> "Check failed: ${result.exceptionOrNull()?.message}"
                        info == null -> "No APK asset found in the latest release"
                        !info.isNewer && manual -> "You are already on the latest version"
                        else -> null
                    },
                    status = if (manual) "Update check complete" else it.status,
                )
            }
        }
    }

    fun install(context: Context) {
        val asset = _state.value.info?.asset ?: return
        download(context, asset.downloadUrl, asset.name) { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            runCatching { context.startActivity(intent) }.onFailure {
                _state.update { s -> s.copy(status = "Install prompt blocked: ${it.message}") }
            }
        }
    }

    fun saveToDevice(context: Context) {
        val asset = _state.value.info?.asset ?: return
        download(context, asset.downloadUrl, asset.name) { file ->
            val saved = saveToDownloads(context, file, asset.name)
            _state.update { it.copy(status = if (saved != null) "Saved to $saved" else "Could not save APK") }
        }
    }

    private fun download(context: Context, url: String, name: String, onDone: (File) -> Unit) {
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true, progress = 0f, status = "Downloading $name…", error = null) }
        viewModelScope.launch {
            runCatching {
                val target = File(context.cacheDir, "update-$name")
                val file = repository.download(url, target) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                _state.update { it.copy(downloading = false, progress = 1f, status = "Download complete") }
                onDone(file)
            }.onFailure { error ->
                _state.update {
                    it.copy(downloading = false, error = "Download failed: ${error.message}", status = null)
                }
            }
        }
    }

    private fun saveToDownloads(context: Context, source: File, name: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { it.copyTo(output) }
            } ?: return@runCatching null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            "Downloads/$name"
        } else {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            directory.mkdirs()
            val target = File(directory, name)
            source.copyTo(target, overwrite = true)
            target.absolutePath
        }
    }.getOrNull()
}
