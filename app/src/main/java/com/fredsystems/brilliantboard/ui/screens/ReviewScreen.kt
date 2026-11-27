package com.fredsystems.brilliantboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fredsystems.brilliantboard.domain.model.GameResult
import com.fredsystems.brilliantboard.domain.model.MoveBadge
import com.fredsystems.brilliantboard.ui.HomeViewModel
import com.fredsystems.brilliantboard.ui.ReviewViewModel
import com.fredsystems.brilliantboard.ui.SettingsViewModel
import com.fredsystems.brilliantboard.ui.board.BoardWithCoords
import com.fredsystems.brilliantboard.ui.board.EvaluationBar
import com.fredsystems.brilliantboard.ui.board.BoardArrow
import com.fredsystems.brilliantboard.ui.common.ChartSeries
import com.fredsystems.brilliantboard.ui.common.EmptyState
import com.fredsystems.brilliantboard.ui.common.LineChart
import com.fredsystems.brilliantboard.ui.common.SectionCard
import com.fredsystems.brilliantboard.ui.common.StatTile
import com.fredsystems.brilliantboard.ui.theme.Accent
import com.fredsystems.brilliantboard.ui.theme.BoardThemes
import com.fredsystems.brilliantboard.ui.theme.Danger
import com.fredsystems.brilliantboard.ui.theme.Good
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReviewScreen(
    homeViewModel: HomeViewModel = koinViewModel(),
    viewModel: ReviewViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val accounts by homeViewModel.state.collectAsState()
    val settings by settingsViewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var resultFilter by remember { mutableStateOf("ALL") }
    var favouritesOnly by remember { mutableStateOf(false) }
    var visibleCount by remember { mutableStateOf(60) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(accounts.accounts.map { it.username }) {
        accounts.accounts.firstOrNull()?.let { viewModel.load(it.username) }
    }

    val filtered = state.games.filter { game ->
        val matchesQuery = query.isBlank() || game.opponent.contains(query, true) ||
            game.opening?.name?.contains(query, true) == true || game.opening?.eco?.contains(query, true) == true
        val matchesResult = resultFilter == "ALL" || game.result.name == resultFilter
        val matchesFavourite = !favouritesOnly || game.id in settings.favorites
        matchesQuery && matchesResult && matchesFavourite
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            SectionCard(title = "Select a game") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    accounts.accounts.forEach { account ->
                        FilterChip(
                            selected = state.selectedAccount == account.username,
                            onClick = { viewModel.load(account.username) },
                            label = { Text(account.username) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Filter by opponent or opening") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "WIN", "DRAW", "LOSS").forEach { filter ->
                        FilterChip(
                            selected = resultFilter == filter,
                            onClick = { resultFilter = filter },
                            label = { Text(filter) },
                        )
                    }
                    FilterChip(
                        selected = favouritesOnly,
                        onClick = { favouritesOnly = !favouritesOnly },
                        label = { Text("★ Saved") },
                    )
                }
                Text("${filtered.size} games available", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (state.games.isEmpty()) {
            item {
                SectionCard(title = "No games yet") {
                    EmptyState("Import an account from Home, then sync to load its games")
                }
            }
        } else {
            items(filtered.take(visibleCount)) { game ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.select(game) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${game.opening?.eco ?: "?"}  ${game.white} vs ${game.black}",
                        Modifier.weight(1f),
                        fontWeight = if (state.selected?.id == game.id) FontWeight.Bold else FontWeight.Normal,
                        color = if (state.selected?.id == game.id) Accent else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (game.id in settings.favorites) "★" else "☆",
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .clickable { settingsViewModel.toggleFavorite(game.id) },
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        game.result.name,
                        color = when (game.result) {
                            GameResult.WIN -> Good
                            GameResult.LOSS -> Danger
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (visibleCount < filtered.size) {
                item {
                    OutlinedButton(
                        onClick = { visibleCount += 60 },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text("Show 60 more games (${filtered.size - visibleCount} remaining)") }
                }
            }
        }

        state.selected?.let { game ->
            item {
                SectionCard(title = "${game.white} vs ${game.black} · ${game.timeClass.label}") {
                    Row(Modifier.fillMaxWidth()) {
                        EvaluationBar(
                            scoreCp = state.report?.evalGraph?.firstOrNull { it.ply >= state.currentPly }?.cp,
                            mate = state.report?.evalGraph?.firstOrNull { it.ply >= state.currentPly }?.mate,
                            whiteToMove = if (state.currentPly == 0) true
                            else game.moves.getOrNull(state.currentPly - 1)?.side ==
                                com.fredsystems.brilliantboard.domain.model.Side.WHITE,
                            modifier = Modifier.height((340 * settings.settings.boardScale).dp),
                        )
                        BoardWithCoords(
                            position = state.position,
                            pieceSet = settings.settings.pieceSet,
                            theme = BoardThemes.byId(settings.settings.boardTheme),
                            flipped = state.flipped,
                            lastMove = state.lastMove,
                            arrows = state.arrows,
                            showLegalMoves = false,
                            boardSize = (340 * settings.settings.boardScale).dp,
                            onSquareClick = {},
                            onMove = { move ->
                                if (state.drill != null) viewModel.attemptDrill(move)
                            },
                        )
                    }
                    state.drill?.let { drill ->
                        Text(
                            drill.feedback ?: "Find the best move",
                            color = if (drill.solved) Good else MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = { viewModel.stopDrill() }) { Text("Exit drill") }
                    }
                    com.fredsystems.brilliantboard.ui.common.MoveList(
                        moves = game.moves,
                        reviews = state.report?.reviews?.associateBy { it.ply }.orEmpty(),
                        currentPly = state.currentPly,
                        onSelect = { viewModel.jumpTo(it) },
                    )
                }
            }

            item {
                SectionCard(title = "Game details") {
                    Text("${game.white} vs ${game.black} · ${game.result.name}",
                        style = MaterialTheme.typography.titleSmall)
                    Text("Opening: ${game.opening?.name ?: "—"} (${game.opening?.eco ?: "—"})",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Time control: ${game.timeControl ?: "—"} · ${game.timeClass.label} · Rated: ${game.rated}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Termination: ${game.termination.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                SectionCard(title = "Review controls") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { viewModel.firstMove() }) { Text("|◀") }
                        OutlinedButton(onClick = { viewModel.step(-1) }) { Text("◀") }
                        OutlinedButton(onClick = { viewModel.step(1) }) { Text("▶") }
                        OutlinedButton(onClick = { viewModel.lastMove() }) { Text("▶|") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.toggleFlip() }) { Text("Flip") }
                        state.report?.reviews?.firstOrNull { it.ply == state.currentPly && it.isCritical }
                            ?.let { review ->
                                Button(onClick = { viewModel.playLine(review.ply) }) {
                                    Text(if (state.linePlaying) "Playing…" else "Show line")
                                }
                                OutlinedButton(onClick = { viewModel.startDrill(review.ply) }) {
                                    Text("Retry mistake")
                                }
                            }
                    }
                    OutlinedButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, game.pgn ?: game.white + " vs " + game.black)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(intent, "Share PGN"),
                        )
                    }, Modifier.fillMaxWidth()) { Text("Share PGN") }
                    Button(
                        onClick = {
                            viewModel.analyse(settings.settings.analysisDepth, settings.settings.multiPv)
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.busy) "Analysing ${(state.progress * 100).toInt()}%" else "Analyse this game")
                    }
                    if (state.busy) {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        state.report?.let { report ->
            item {
                SectionCard(title = "Accuracy & performance") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("White accuracy", "${report.whiteAccuracy.toInt()}%", Good, Modifier.weight(1f))
                        StatTile("Black accuracy", "${report.blackAccuracy.toInt()}%", Accent, Modifier.weight(1f))
                        StatTile("Blunders", report.blunders.toString(), Danger, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("White Elo", report.whitePerformanceElo.toString(), modifier = Modifier.weight(1f))
                        StatTile("Black Elo", report.blackPerformanceElo.toString(), modifier = Modifier.weight(1f))
                        StatTile("Brilliant", report.brilliancies.toString(), Accent, Modifier.weight(1f))
                    }
                }
            }
            item {
                SectionCard(title = "Evaluation graph · drag to explore") {
                    com.fredsystems.brilliantboard.ui.common.InteractiveEvalGraph(
                        points = report.evalGraph,
                        currentPly = state.currentPly,
                        onScrub = { viewModel.jumpTo(it) },
                    )
                }
            }
            item {
                SectionCard(title = "Critical moves") {
                    report.reviews.filter { it.isCritical }.take(30).forEach { review ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                "${review.ply / 2 + 1}. ${review.san} ${review.badge.symbol}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "-${review.winLoss.toInt()}%",
                                color = Danger,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
