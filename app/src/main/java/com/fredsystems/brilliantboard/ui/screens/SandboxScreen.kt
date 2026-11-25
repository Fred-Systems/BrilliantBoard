package com.fredsystems.brilliantboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.brilliantboard.ui.SandboxViewModel
import com.fredsystems.brilliantboard.ui.board.BoardWithCoords
import com.fredsystems.brilliantboard.ui.board.EvaluationBar
import com.fredsystems.brilliantboard.ui.common.SectionCard
import com.fredsystems.brilliantboard.ui.common.StatTile
import com.fredsystems.brilliantboard.ui.theme.Accent
import com.fredsystems.brilliantboard.ui.theme.BoardTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun SandboxScreen(
    viewModel: SandboxViewModel = koinViewModel(),
    settingsViewModel: com.fredsystems.brilliantboard.ui.SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val settings by settingsViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            SectionCard(title = "Free analysis / sandbox") {
                Row(Modifier.fillMaxWidth()) {
                    EvaluationBar(winPercent = state.winPercent ?: 50f, modifier = Modifier.height(340.dp))
                    BoardWithCoords(
                        position = state.position,
                        pieceSet = settings.settings.pieceSet,
                        theme = com.fredsystems.brilliantboard.ui.theme.BoardThemes.byId(settings.settings.boardTheme),
                        flipped = state.flipped,
                        lastMove = state.lastMove,
                        showLegalMoves = settings.settings.showLegalMoves,
                        boardSize = (340 * settings.settings.boardScale).dp,
                        onSquareClick = {},
                        onMove = { viewModel.onMove(it) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.toggleFlip() }) { Text("Flip") }
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("Reset") }
                    Button(onClick = { viewModel.evaluate() }) { Text("Evaluate") }
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Engine", if (state.engineAvailable) "Ready" else "Missing", Accent, Modifier.weight(1f))
                    StatTile("Eval", state.winPercent?.let { "${it.toInt()}%" } ?: "—", modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            SectionCard(title = "Load FEN / PGN") {
                OutlinedTextField(
                    value = state.fenInput,
                    onValueChange = { viewModel.setFen(it) },
                    label = { Text("FEN") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { viewModel.loadFen() }, Modifier.fillMaxWidth()) { Text("Load position") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.pgnInput,
                    onValueChange = { viewModel.setPgn(it) },
                    label = { Text("PGN") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { viewModel.loadPgn() }, Modifier.fillMaxWidth()) { Text("Replay PGN") }
            }
        }

        item {
            SectionCard(title = "Lichess opening explorer") {
                Text("Opening explorer statistics overlay is available through ExplorerApi.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    scope.launch {
                        val suggestions = viewModel.explorerSuggestions(state.position.fen())
                        viewModel.setExplorerInfo(
                            suggestions.take(5).joinToString("\n") {
                                "${it.san}: W ${it.whitePercent.toInt()}% / D ${it.drawPercent.toInt()}% / B ${it.blackPercent.toInt()}%"
                            }
                        )
                    }
                }, Modifier.fillMaxWidth()) { Text("Fetch explorer stats") }
                if (state.explorerInfo.isNotBlank()) {
                    Text(state.explorerInfo, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
