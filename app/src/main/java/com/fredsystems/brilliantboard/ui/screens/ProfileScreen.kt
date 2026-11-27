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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fredsystems.brilliantboard.domain.scouting.EndgameType
import com.fredsystems.brilliantboard.domain.scouting.GamePhase
import com.fredsystems.brilliantboard.ui.MyProfileViewModel
import com.fredsystems.brilliantboard.ui.common.BarChart
import com.fredsystems.brilliantboard.ui.common.ChartSeries
import com.fredsystems.brilliantboard.ui.common.EmptyState
import com.fredsystems.brilliantboard.ui.common.Heatmap
import com.fredsystems.brilliantboard.ui.common.LineChart
import com.fredsystems.brilliantboard.ui.common.ProgressRow
import com.fredsystems.brilliantboard.ui.common.SafeContent
import com.fredsystems.brilliantboard.ui.common.SectionCard
import com.fredsystems.brilliantboard.ui.common.StatTile
import com.fredsystems.brilliantboard.ui.common.WdlBar
import com.fredsystems.brilliantboard.ui.theme.Accent
import com.fredsystems.brilliantboard.ui.theme.Danger
import com.fredsystems.brilliantboard.ui.theme.Good
import com.fredsystems.brilliantboard.ui.theme.Warning
import org.koin.androidx.compose.koinViewModel

@Composable
fun MyProfileScreen(viewModel: MyProfileViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val data = state.dashboard
    val context = LocalContext.current

    SafeContent {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.forEach { account ->
                    FilterChip(
                        selected = state.selected?.username == account.username,
                        onClick = { viewModel.select(account) },
                        label = { Text("${account.username} · ${account.platform.label}") },
                    )
                }
            }
        }

        if (data.profile == null) {
            item { EmptyState("Link an account from Home to unlock your player intelligence dashboard") }
        } else {
            item {
                SectionCard(title = "${data.profile.displayLabel} · player intelligence") {
                    WdlBar(data.wdl)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Games analysed", data.games.size.toString(), modifier = Modifier.weight(1f))
                        StatTile("Score", "${data.wdl.scorePercent.toInt()}%", Good, Modifier.weight(1f))
                        StatTile("W-D-L", "${data.wdl.wins}-${data.wdl.draws}-${data.wdl.losses}", modifier = Modifier.weight(1f))
                    }
                    val best = data.openings.filter { it.games >= 2 }.maxByOrNull { it.wdl.scorePercent }
                    val worst = data.openings.filter { it.games >= 2 }.minByOrNull { it.wdl.scorePercent }
                    Spacer(Modifier.height(8.dp))
                    Text("Best opening: ${best?.let { "${it.eco} ${it.openingName} — ${it.wdl.scorePercent.toInt()}% (${it.games})" } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall, color = Good)
                    Text("Worst opening: ${worst?.let { "${it.eco} ${it.openingName} — ${it.wdl.scorePercent.toInt()}% (${it.games})" } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall, color = Danger)
                    OutlinedButton(onClick = {
                        val summary = buildString {
                            append("BrilliantBoard report for ${data.profile.displayLabel}\n")
                            append("Games: ${data.games.size}, Score: ${data.wdl.scorePercent.toInt()}% (${data.wdl.wins}/${data.wdl.draws}/${data.wdl.losses})\n")
                            append("Avg seconds/move: ${data.clock.avgSecondsPerMove.toInt()}\n")
                            append("Time trouble: ${data.clock.timeTroubleBlunderRate.toInt()}%, Berserk: ${data.clock.berserkRate.toInt()}%\n")
                            append("Tilt after 2 losses: ${data.tilt.afterTwoLosses.toInt()}% (baseline ${data.tilt.baselineWinRate.toInt()}%)\n")
                            worst?.let { append("Leak: ${it.eco} ${it.openingName} ${it.wdl.scorePercent.toInt()}%\n") }
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, summary)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share report"))
                    }, Modifier.fillMaxWidth()) { Text("Share player report") }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            item {
                SectionCard(title = "Rating history") {
                    LineChart(
                        series = data.ratingCurves.mapIndexed { index, curve ->
                            ChartSeries(curve.label, curve.points, listOf(Accent, Good, Warning, Danger)[index % 4])
                        },
                    )
                }
            }

            item {
                SectionCard(title = "Opening leak finder") {
                    if (data.openings.isEmpty()) Text("No ECO-tagged games yet", style = MaterialTheme.typography.bodySmall)
                    else data.openings.take(10).forEach { stat ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("${stat.eco}  ${stat.openingName}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text("${stat.wdl.scorePercent.toInt()}% · ${stat.games} games", color = Danger, style = MaterialTheme.typography.bodySmall)
                            }
                            WdlBar(stat.wdl)
                            stat.collapsePly?.let { Text("Collapse around move $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Time management & clock IQ") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("s / move", data.clock.avgSecondsPerMove.toInt().toString(), modifier = Modifier.weight(1f))
                        StatTile("Time trouble", "${data.clock.timeTroubleBlunderRate.toInt()}%", Danger, Modifier.weight(1f))
                        StatTile("Berserk", "${data.clock.berserkRate.toInt()}%", Warning, Modifier.weight(1f))
                        StatTile("Timeouts", "${data.clock.timeoutLossRate.toInt()}%", Danger, Modifier.weight(1f))
                    }
                    Text("Time trouble = blunders made with under 30s left. Berserk = blunders with over half the clock remaining.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                SectionCard(title = "Phase mastery") {
                    data.phases.forEach { phase ->
                        ProgressRow("${phase.phase.label} · ${phase.games} games", phase.wdl.scorePercent, when (phase.phase) {
                            GamePhase.OPENING -> Accent
                            GamePhase.MIDDLEGAME -> Warning
                            GamePhase.ENDGAME -> Good
                        })
                    }
                }
            }

            item {
                SectionCard(title = "Endgame conversion lab (+2.0 advantage)") {
                    data.endgames.forEach { stat ->
                        ProgressRow("${stat.type.label} · ${stat.games} games", stat.conversionRate,
                            if (stat.conversionRate >= 60f) Good else Warning)
                    }
                    val bestEg = data.endgames.filter { it.games > 0 }.maxByOrNull { it.conversionRate }
                    bestEg?.let {
                        Text("Strongest: ${it.type.label}, converting ${it.conversionRate.toInt()}% of winning endgames",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                SectionCard(title = "Tilt & bio-rhythm") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Baseline", "${data.tilt.baselineWinRate.toInt()}%", modifier = Modifier.weight(1f))
                        StatTile("After 1", "${data.tilt.afterOneLoss.toInt()}%", Warning, Modifier.weight(1f))
                        StatTile("After 2", "${data.tilt.afterTwoLosses.toInt()}%", Danger, Modifier.weight(1f))
                        StatTile("After 3+", "${data.tilt.afterThreePlusLosses.toInt()}%", Danger, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Win rate by hour of day", style = MaterialTheme.typography.bodySmall)
                    BarChart(data.tilt.hourlyHeatmap, (0..23).filter { it % 4 == 0 }.map { "$it" })
                    Spacer(Modifier.height(6.dp))
                    Text("Win rate by weekday", style = MaterialTheme.typography.bodySmall)
                    Heatmap(data.tilt.weekdayHeatmap, columns = 7)
                }
            }

            item {
                SectionCard(title = "Tactical motifs & style") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Missed mates", data.tactics.missedMateIn1.toString(), Danger, Modifier.weight(1f))
                        StatTile("Hanging", data.tactics.hangingPieces.toString(), Warning, Modifier.weight(1f))
                        StatTile("Forks", data.tactics.forks.toString(), modifier = Modifier.weight(1f))
                        StatTile("Pins", data.tactics.pins.toString(), modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    data.style?.let { style ->
                        ProgressRow("Aggression", style.aggressionScore, Danger)
                        ProgressRow("Trade happiness", style.tradeHappiness)
                        ProgressRow("Flagging vulnerability", style.flaggingVulnerability, Warning)
                        ProgressRow("Tilt susceptibility", style.tiltSusceptibility, Danger)
                        ProgressRow("Repertoire predictability", style.predictability, Accent)
                        Text("Aggression measures early pawn storms and sacrifices; trade happiness is the queen-trade rate.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

}

private val EndgameType.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }
