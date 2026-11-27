package com.fredsystems.brilliantboard.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredsystems.brilliantboard.domain.model.MoveBadge
import com.fredsystems.brilliantboard.domain.model.MoveRecord
import com.fredsystems.brilliantboard.domain.model.MoveReview
import com.fredsystems.brilliantboard.ui.theme.Accent
import com.fredsystems.brilliantboard.ui.theme.Danger
import com.fredsystems.brilliantboard.ui.theme.Good

/** Chess.com-style two-column move list with classification glyphs. */
@Composable
fun MoveList(
    moves: List<MoveRecord>,
    reviews: Map<Int, MoveReview>,
    currentPly: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        moves.chunked(2).forEachIndexed { index, pair ->
            val number = index + 1
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { pair.firstOrNull()?.let { onSelect(it.ply) } }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$number.",
                    Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                pair.forEach { move ->
                    val badge = reviews[move.ply]?.badge
                    Text(
                        move.san + (badge?.symbol ?: ""),
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        color = when {
                            move.ply == currentPly -> Accent
                            badge == MoveBadge.BLUNDER || badge == MoveBadge.MISSED_MATE -> Danger
                            badge in setOf(MoveBadge.BRILLIANT, MoveBadge.GREAT) -> Good
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (move.ply == currentPly) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                    )
                }
                if (pair.size == 1) Text("", Modifier.weight(1f))
            }
        }
    }
}
