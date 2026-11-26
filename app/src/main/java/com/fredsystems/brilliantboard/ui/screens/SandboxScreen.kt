package com.fredsystems.brilliantboard.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredsystems.brilliantboard.data.remote.ExplorerApi
import com.fredsystems.brilliantboard.data.remote.MoveSuggestion
import com.fredsystems.brilliantboard.ui.SandboxViewModel
import com.fredsystems.brilliantboard.ui.SettingsViewModel
import com.fredsystems.brilliantboard.ui.board.BoardWithCoords
import com.fredsystems.brilliantboard.ui.board.EvaluationBar
import com.fredsystems.brilliantboard.ui.common.SectionCard
import com.fredsystems.brilliantboard.ui.common.StatTile
import com.fredsystems.brilliantboard.ui.theme.Accent
import com.fredsystems.brilliantboard.ui.theme.BoardThemes
import com.fredsystems.brilliantboard.ui.theme.Danger
import com.fredsystems.brilliantboard.ui.theme.Good
import com.fredsystems.brilliantboard.ui.theme.Warning
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun SandboxScreen(
    viewModel: SandboxViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val settings by settingsViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            SectionCard(title = "Free analysis / sandbox") {
                Row(Modifier.fillMaxWidth()) {
                    EvaluationBar(winPercent = state.winPercent ?: 50f, modifier = Modifier.height((340 * settings.settings.boardScale).dp))
                    BoardWithCoords(
                        position = state.position,
                        pieceSet = settings.settings.pieceSet,
                        theme = BoardThemes.byId(settings.settings.boardTheme),
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
                    OutlinedButton(onClick = { viewModel.undo() }) { Text("Undo") }
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("Reset") }
                    Button(onClick = { viewModel.evaluate() }) { Text("Evaluate") }
                }
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, state.position.fen())
                    }
                    context.startActivity(Intent.createChooser(intent, "Share FEN"))
                }, Modifier.fillMaxWidth()) { Text("Share current FEN") }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Engine", if (state.engineAvailable) "Ready" else "Missing", Accent, Modifier.weight(1f))
                    StatTile("Eval", state.evalLabel, modifier = Modifier.weight(1f))
                    StatTile("Material", materialLabel(state.position), modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            SectionCard(title = "Lichess opening explorer") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExplorerApi.SOURCES.forEach { source ->
                        FilterChip(
                            selected = state.explorerSource == source,
                            onClick = { viewModel.setExplorerSource(source) },
                            label = { Text(source.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = { viewModel.loadExplorer() }, Modifier.fillMaxWidth()) {
                    Text(if (state.explorerLoading) "Loading…" else "Explore this position")
                }
                if (state.explorerInfo.isNotBlank()) {
                    Text(state.explorerInfo, style = MaterialTheme.typography.titleSmall)
                }
                if (state.explorerLoading) LinearProgressIndicator(
                    progress = { 0.5f },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.suggestions.isEmpty() && !state.explorerLoading) {
                    Text(
                        "Tap a move below to play it on the board. Win/Draw/Loss bars update for every candidate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(state.suggestions) { suggestion ->
            ExplorerMoveRow(suggestion) { viewModel.playUci(suggestion.uci) }
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
            SectionCard(title = "Engine evaluation") {
                Button(onClick = {
                    scope.launch { viewModel.evaluate() }
                }, Modifier.fillMaxWidth()) { Text("Run Stockfish analysis") }
            }
        }
    }
}

private fun materialLabel(position: com.fredsystems.brilliantboard.domain.chess.Position): String {
    val values = mapOf('p' to 1, 'n' to 3, 'b' to 3, 'r' to 5, 'q' to 9)
    var score = 0
    position.squares.forEach { piece ->
        if (piece == '.') return@forEach
        score += if (piece.isUpperCase()) values[piece.lowercaseChar()] ?: 0
        else -(values[piece.lowercaseChar()] ?: 0)
    }
    return when {
        score > 0 -> "+${score}"
        score < 0 -> score.toString()
        else -> "="
    }
}

@Composable
private fun ExplorerMoveRow(suggestion: MoveSuggestion, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            suggestion.san,
            Modifier.weight(0.6f),
            color = Accent,
            style = MaterialTheme.typography.titleMedium,
        )
        Column(Modifier.weight(2f)) {
            Row(Modifier.fillMaxWidth().height(10.dp)) {
                Box(Modifier.weight(suggestion.whitePercent.coerceAtLeast(0.1f)).background(Good))
                Box(Modifier.weight(suggestion.drawPercent.coerceAtLeast(0.1f)).background(Warning))
                Box(Modifier.weight(suggestion.blackPercent.coerceAtLeast(0.1f)).background(Danger))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("W ${suggestion.whitePercent.toInt()}%", color = Good, fontSize = 10.sp)
                Text("D ${suggestion.drawPercent.toInt()}%", color = Warning, fontSize = 10.sp)
                Text("B ${suggestion.blackPercent.toInt()}%", color = Danger, fontSize = 10.sp)
            }
        }
        Text(
            "${suggestion.sampleSize / 1000}k".takeIf { suggestion.sampleSize >= 1000 }
                ?: suggestion.sampleSize.toString(),
            Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
        )
    }
}
