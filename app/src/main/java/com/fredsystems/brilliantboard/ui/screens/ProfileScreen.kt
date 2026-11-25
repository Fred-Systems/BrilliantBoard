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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.brilliantboard.domain.scouting.GamePhase
import com.fredsystems.brilliantboard.ui.MyProfileViewModel
import com.fredsystems.brilliantboard.ui.common.BarChart
import com.fredsystems.brilliantboard.ui.common.ChartSeries
import com.fredsystems.brilliantboard.ui.common.EmptyState
import com.fredsystems.brilliantboard.ui.common.Heatmap
import com.fredsystems.brilliantboard.ui.common.LineChart
import com.fredsystems.brilliantboard.ui.common.ProgressRow
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

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.forEach { account ->
                    FilterChip(
                        selected = state.selected?.username == account.username,
                        onClick = { viewModel.select(account) },
                        label = { Text("${account.username} (${account.platform.label})") },
                    )
                }
            }
        }

        if (data.profile == null) {
            item { EmptyState("Link an account from Home to see your dashboard") }
        } else {
            item {
                SectionCard(title = "${data.profile.displayLabel} · ${data.profile.platform.label}") {
                    WdlBar(data.wdl)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Games", data.wdl.total.toString(), modifier = Modifier.weight(1f))
                        StatTile("Score", "${data.wdl.scorePercent.toInt()}%", Good, Modifier.weight(1f))
                        StatTile("Openings", data.openings.size.toString(), modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionCard(title = "Unified rating curve") {
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
                    else data.openings.take(8).forEach { stat ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("${stat.eco}  ${stat.openingName}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text("${stat.wdl.scorePercent.toInt()}% (${stat.games})", color = Danger, style = MaterialTheme.typography.bodySmall)
                            }
                            WdlBar(stat.wdl)
                            stat.collapsePly?.let {
                                Text("Collapse around move $it", style = MaterialTheme.typography.bodySmall)
                            }
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
                }
            }

            item {
                SectionCard(title = "Phase mastery") {
                    data.phases.forEach { phase ->
                        ProgressRow(phase.phase.label, phase.wdl.scorePercent, when (phase.phase) {
                            GamePhase.OPENING -> Accent
                            GamePhase.MIDDLEGAME -> Warning
                            GamePhase.ENDGAME -> Good
                        })
                    }
                }
            }

            item {
                SectionCard(title = "Endgame conversion lab (+2.0)") {
                    data.endgames.forEach { stat ->
                        ProgressRow(stat.type.name, stat.conversionRate, if (stat.conversionRate >= 60f) Good else Warning)
                    }
                }
            }

            item {
                SectionCard(title = "Tilt & bio-rhythm") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Baseline", "${data.tilt.baselineWinRate.toInt()}%", modifier = Modifier.weight(1f))
                        StatTile("After 1 loss", "${data.tilt.afterOneLoss.toInt()}%", Warning, Modifier.weight(1f))
                        StatTile("After 2", "${data.tilt.afterTwoLosses.toInt()}%", Danger, Modifier.weight(1f))
                        StatTile("After 3+", "${data.tilt.afterThreePlusLosses.toInt()}%", Danger, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Win rate by hour", style = MaterialTheme.typography.bodySmall)
                    BarChart(data.tilt.hourlyHeatmap, (0..23).filter { it % 4 == 0 }.map { "$it" })
                    Spacer(Modifier.height(8.dp))
                    Text("Win rate by weekday", style = MaterialTheme.typography.bodySmall)
                    Heatmap(data.tilt.weekdayHeatmap, columns = 7)
                }
            }

            item {
                SectionCard(title = "Tactical motif weaknesses") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Missed mates", data.tactics.missedMateIn1.toString(), Danger, Modifier.weight(1f))
                        StatTile("Hanging", data.tactics.hangingPieces.toString(), Warning, Modifier.weight(1f))
                        StatTile("Forks", data.tactics.forks.toString(), modifier = Modifier.weight(1f))
                        StatTile("Pins", data.tactics.pins.toString(), modifier = Modifier.weight(1f))
                    }
                }
            }

            data.style?.let { style ->
                item {
                    SectionCard(title = "Style profile") {
                        ProgressRow("Aggression", style.aggressionScore, Danger)
                        ProgressRow("Trade happiness", style.tradeHappiness, Accent)
                        ProgressRow("Flagging vulnerability", style.flaggingVulnerability, Warning)
                        ProgressRow("Tilt susceptibility", style.tiltSusceptibility, Danger)
                    }
                }
            }
        }
    }
}
