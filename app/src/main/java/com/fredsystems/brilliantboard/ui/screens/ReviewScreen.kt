package com.fredsystems.brilliantboard.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.brilliantboard.domain.model.MoveBadge
import com.fredsystems.brilliantboard.ui.HomeViewModel
import com.fredsystems.brilliantboard.ui.ReviewViewModel
import com.fredsystems.brilliantboard.ui.board.BoardWithCoords
import com.fredsystems.brilliantboard.ui.board.EvaluationBar
import com.fredsystems.brilliantboard.ui.common.ChartSeries
import com.fredsystems.brilliantboard.ui.common.EmptyState
import com.fredsystems.brilliantboard.ui.common.LineChart
import com.fredsystems.brilliantboard.ui.common.SectionCard
import com.fredsystems.brilliantboard.ui.common.StatTile
import com.fredsystems.brilliantboard.ui.theme.Accent
import com.fredsystems.brilliantboard.ui.theme.BoardTheme
import com.fredsystems.brilliantboard.ui.theme.Danger
import com.fredsystems.brilliantboard.ui.theme.Good
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReviewScreen(
    homeViewModel: HomeViewModel = koinViewModel(),
    viewModel: ReviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val accounts by homeViewModel.state.collectAsState()

    LaunchedEffect(accounts.accounts.map { it.username }) {
        accounts.accounts.firstOrNull()?.let { viewModel.load(it.username) }
    }

    val boardTheme = BoardTheme.EMERALD

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            SectionCard(title = "Game review") {
                if (state.games.isEmpty()) {
                    EmptyState("Import games from Home to review them")
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        state.games.take(40).forEach { game ->
                            OutlinedButton(
                                onClick = { viewModel.select(game) },
                                modifier = Modifier.padding(end = 6.dp),
                            ) {
                                Text("${game.opponent} ${game.result.name} · ${game.opening?.eco ?: "?"}")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.analyse() },
                        enabled = !state.busy && state.selected != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.busy) "Analysing ${(state.progress * 100).toInt()}%" else "Analyse with Stockfish") }
                    if (state.busy) LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        state.selected?.let { game ->
            item {
                SectionCard(title = "${game.white} vs ${game.black}") {
                    Row(Modifier.fillMaxWidth()) {
                        EvaluationBar(
                            winPercent = state.report?.evalGraph?.firstOrNull { it.ply >= state.currentPly }?.winPercent
                                ?: 50f,
                            modifier = Modifier.height(340.dp),
                        )
                        BoardWithCoords(
                            position = state.position,
                            theme = boardTheme,
                            flipped = state.flipped,
                            lastMove = state.lastMove,
                            arrows = state.arrows,
                            onSquareClick = {},
                            onMove = {},
                        )
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        game.moves.forEach { move ->
                            val badge = state.report?.reviews?.firstOrNull { it.ply == move.ply }?.badge
                            OutlinedButton(onClick = { viewModel.jumpTo(move.ply) }) {
                                Text(
                                    text = if (badge?.symbol?.isNotBlank() == true) "${move.san}${badge.symbol}" else move.san,
                                    color = when (badge) {
                                        MoveBadge.BLUNDER -> Danger
                                        MoveBadge.MISTAKE, MoveBadge.INACCURACY -> MaterialTheme.colorScheme.tertiary
                                        MoveBadge.BRILLIANT -> Accent
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.toggleFlip() }) { Text("Flip") }
                        state.report?.reviews?.firstOrNull {
                            it.ply == state.currentPly && it.isCritical
                        }?.let { review ->
                            Button(onClick = { viewModel.showBestLine(review.ply) }) { Text("Show best line") }
                            OutlinedButton(onClick = { viewModel.jumpTo(review.ply) }) { Text("Retry mistake") }
                        }
                    }
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
                SectionCard(title = "Evaluation graph") {
                    LineChart(
                        series = listOf(ChartSeries("Win %", report.evalGraph.map { it.winPercent }, Accent)),
                        minValue = 0f,
                        maxValue = 100f,
                    )
                    Text("Tap any move above to jump to that evaluation spike",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                SectionCard(title = "Move classification") {
                    report.reviews.filter { it.isCritical }.take(30).forEach { review ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${review.ply / 2 + 1}. ${review.san} ${review.badge.symbol}",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text("-${review.winLoss.toInt()}%", color = Danger, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
