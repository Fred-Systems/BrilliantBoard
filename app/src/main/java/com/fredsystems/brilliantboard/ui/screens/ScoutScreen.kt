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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.ui.ScoutViewModel
import com.fredsystems.brilliantboard.ui.common.EmptyState
import com.fredsystems.brilliantboard.ui.common.ProgressRow
import com.fredsystems.brilliantboard.ui.common.SectionCard
import com.fredsystems.brilliantboard.ui.common.StatTile
import com.fredsystems.brilliantboard.ui.theme.Accent
import com.fredsystems.brilliantboard.ui.theme.Danger
import com.fredsystems.brilliantboard.ui.theme.Warning
import org.koin.androidx.compose.koinViewModel

@Composable
fun ScoutScreen(
    viewModel: ScoutViewModel = koinViewModel(),
    searchViewModel: com.fredsystems.brilliantboard.ui.SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            SectionCard(title = "Opponent prep") {
                com.fredsystems.brilliantboard.ui.common.UsernameSearchField(
                    value = state.query,
                    onValueChange = { viewModel.setQuery(it); searchViewModel.search(it) },
                    label = "Username to scout (smart search)",
                    searchViewModel = searchViewModel,
                    onSelected = { name, suggestedPlatform ->
                        viewModel.setQuery(name)
                        suggestedPlatform?.let { viewModel.setPlatform(it) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Platform.entries.forEach { platform ->
                        FilterChip(
                            selected = state.platform == platform,
                            onClick = { viewModel.setPlatform(platform) },
                            label = { Text(platform.label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.search() },
                    enabled = !state.busy && state.query.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.busy) "Building dossier…" else "Generate gameplan card") }
                state.message?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
            }
        }

        val dossier = state.dossier
        if (dossier == null) {
            item { EmptyState("Search for any player to reveal their weaknesses and a tailored gameplan") }
        } else {
            item {
                SectionCard(title = "Gameplan vs ${dossier.opponentName}") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Games", dossier.gamesAnalyzed.toString(), modifier = Modifier.weight(1f))
                        StatTile("H2H", dossier.headToHead.games.toString(), Accent, Modifier.weight(1f))
                        StatTile("Score", "${dossier.headToHead.wdl.scorePercent.toInt()}%", modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionCard(title = "Recommended gameplan") {
                    dossier.recommendations.forEach { recommendation ->
                        Text("• $recommendation", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            item {
                SectionCard(title = "Opening traps & lines") {
                    Text("Weakest as White", style = MaterialTheme.typography.titleSmall)
                    dossier.worstAsWhite.forEach {
                        Text("${it.openingName.ifBlank { it.eco }} — ${it.wdl.scorePercent.toInt()}% (${it.games})",
                            style = MaterialTheme.typography.bodySmall, color = Danger)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Weakest as Black", style = MaterialTheme.typography.titleSmall)
                    dossier.worstAsBlack.forEach {
                        Text("${it.openingName.ifBlank { it.eco }} — ${it.wdl.scorePercent.toInt()}% (${it.games})",
                            style = MaterialTheme.typography.bodySmall, color = Danger)
                    }
                }
            }

            item {
                SectionCard(title = "Repertoire breadth & predictability") {
                    dossier.topFirstMoves.forEach { move ->
                        ProgressRow(move.move, move.percentage, Accent)
                    }
                    Text("Most played: ${dossier.style.mostPlayedFirstMove ?: "—"}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                SectionCard(title = "Style & psychology") {
                    ProgressRow("Aggression", dossier.style.aggressionScore, Danger)
                    ProgressRow("Trade happiness", dossier.style.tradeHappiness)
                    ProgressRow("Flagging vulnerability", dossier.style.flaggingVulnerability, Warning)
                    ProgressRow("Tilt susceptibility", dossier.style.tiltSusceptibility, Danger)
                    ProgressRow("Score after 2 losses", dossier.tilt.afterTwoLosses, Warning)
                }
            }
        }
    }
}
