package com.fredsystems.brilliantboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.ui.HomeViewModel
import com.fredsystems.brilliantboard.ui.common.EmptyState
import com.fredsystems.brilliantboard.ui.common.SectionCard
import com.fredsystems.brilliantboard.ui.common.StatTile
import com.fredsystems.brilliantboard.ui.navigation.Screen
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var username by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf(Platform.CHESS_COM) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("BrilliantBoard", style = MaterialTheme.typography.headlineMedium)
            Text("Chess intelligence & game review", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Accounts", state.accounts.size.toString(), modifier = Modifier.weight(1f))
                StatTile("Games", state.totalGames.toString(), modifier = Modifier.weight(1f))
            }
        }

        item {
            SectionCard(title = "Link your account") {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Platform.entries.forEach { option ->
                        FilterChip(
                            selected = platform == option,
                            onClick = { platform = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { if (username.isNotBlank()) viewModel.link(username.trim(), platform) },
                    enabled = !state.busy && username.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.busy) "Importing…" else "Import & sync") }
            }
        }

        item {
            SectionCard(title = "My profiles", trailing = {
                Button(onClick = { viewModel.syncAll() }, enabled = !state.busy) { Text("Sync all") }
            }) {
                if (state.accounts.isEmpty()) {
                    EmptyState("Link a Chess.com or Lichess account to begin")
                } else {
                    state.accounts.forEach { account ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("${account.displayLabel} · ${account.platform.label}", Modifier.weight(1f))
                            Text(account.ratings.values.sortedDescending().firstOrNull()?.toString() ?: "—")
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Tools") {
                listOf(
                    Screen.MyProfile to "Opening leaks, clock IQ, tilt & phase analytics",
                    Screen.Scout to "Build an opponent preparation dossier",
                    Screen.Review to "Stockfish review with accuracy and badges",
                    Screen.Sandbox to "Free analysis, FEN/PGN and opening explorer",
                ).forEach { (screen, description) ->
                    Button(
                        onClick = { onNavigate(screen.route) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Column(horizontalAlignment = Alignment.Start) {
                        Text(screen.label)
                        Text(description, style = MaterialTheme.typography.bodySmall)
                    } }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        state.message?.let { message ->
            item { Text(message, color = MaterialTheme.typography.bodySmall.color) }
        }
    }
}
