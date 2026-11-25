package com.fredsystems.brilliantboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.brilliantboard.ui.SettingsViewModel
import com.fredsystems.brilliantboard.ui.common.SectionCard
import com.fredsystems.brilliantboard.ui.theme.BoardTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val settings = state.settings

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            SectionCard(title = "Board theme") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = settings.boardTheme.equals(theme.name, true),
                            onClick = { viewModel.setTheme(theme.name) },
                            label = { Text(theme.name) },
                        )
                    }
                }
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
            }
        }
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
