package com.fredsystems.brilliantboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fredsystems.brilliantboard.BuildConfig
import com.fredsystems.brilliantboard.ui.SettingsViewModel
import com.fredsystems.brilliantboard.ui.UpdateViewModel
import com.fredsystems.brilliantboard.ui.board.PieceImage
import com.fredsystems.brilliantboard.ui.common.SectionCard
import com.fredsystems.brilliantboard.ui.theme.BoardTheme
import com.fredsystems.brilliantboard.ui.theme.BoardThemes
import com.fredsystems.brilliantboard.ui.theme.PieceSets
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    updateViewModel: UpdateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val updateState by updateViewModel.state.collectAsState()
    val settings = state.settings
    val context = LocalContext.current

    LaunchedEffect(settings.autoCheckUpdates) {
        if (settings.autoCheckUpdates && updateState.info == null) updateViewModel.check(manual = false)
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            SectionCard(title = "Board & Pieces") {
                var expanded by remember { mutableStateOf(false) }
                Text("Piece style", style = MaterialTheme.typography.bodySmall)
                Box {
                    OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) {
                        Text(PieceSets.byId(settings.pieceSet).label)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        PieceSets.all.forEach { set ->
                            DropdownMenuItem(
                                text = { Text(set.label) },
                                onClick = { viewModel.setPieceSet(set.id); expanded = false },
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf('K', 'Q', 'R', 'B', 'N', 'P', 'k', 'q', 'r', 'b', 'n', 'p').forEach { piece ->
                        PieceImage(piece, settings.pieceSet, Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Board theme: ${BoardThemes.byId(settings.boardTheme).label}", style = MaterialTheme.typography.bodySmall)
                BoardThemes.all.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { theme -> ThemeSwatch(theme, settings.boardTheme) { viewModel.setTheme(it.id) } }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text("Board size: ${(settings.boardScale * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = settings.boardScale,
                    onValueChange = { viewModel.setBoardScale(it) },
                    valueRange = 0.6f..1.5f,
                )
            }
        }

        item {
            SectionCard(title = "Engine") {
                Text("Search depth: ${settings.analysisDepth} (12–22)", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12, 16, 18, 20, 22).forEach { depth ->
                        FilterChip(
                            selected = settings.analysisDepth == depth,
                            onClick = { viewModel.setDepth(depth) },
                            label = { Text("$depth") },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Display & sound") {
                ToggleRow("Move sounds", settings.soundEnabled) { viewModel.setSound(it) }
                ToggleRow("Dynamic colour", settings.dynamicColor) { viewModel.setDynamic(it) }
                ToggleRow("Check for updates on launch", settings.autoCheckUpdates) { viewModel.setAutoUpdates(it) }
            }
        }

        item {
            SectionCard(title = "Updates · current v${BuildConfig.VERSION_NAME}") {
                Button(onClick = { updateViewModel.check() }, Modifier.fillMaxWidth()) {
                    Text(if (updateState.checking) "Checking…" else "Check for updates")
                }
                updateState.info?.let { info ->
                    Spacer(Modifier.height(8.dp))
                    Text("Latest: ${info.name} (${info.version})", style = MaterialTheme.typography.titleSmall)
                    if (info.isNewer) {
                        Text("A new version is available.", color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { updateViewModel.install(context) }, Modifier.fillMaxWidth()) {
                            Text("Update now")
                        }
                    }
                    OutlinedButton(onClick = { updateViewModel.saveToDevice(context) }, Modifier.fillMaxWidth()) {
                        Text("Save latest APK to device")
                    }
                    if (info.notes.isNotBlank()) {
                        Text(info.notes.take(600), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (updateState.downloading) {
                    LinearProgressIndicator(
                        progress = { updateState.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                updateState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                updateState.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: BoardTheme, selectedId: String, onClick: (BoardTheme) -> Unit) {
    val selected = theme.id == selectedId
    Column(
        Modifier
            .size(52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .border(if (selected) 2.dp else 0.dp, androidx.compose.ui.graphics.Color(0xFFB9A8FF),
                androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable { onClick(theme) },
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).background(theme.light))
        Box(Modifier.fillMaxWidth().weight(1f).background(theme.dark))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
