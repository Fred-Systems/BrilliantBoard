package com.fredsystems.brilliantboard.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Traditional vertical evaluation bar: white fills from the bottom, black from the top,
 * animated smoothly between positions. Engine centipawns are relative to the side to move.
 */
@Composable
fun EvaluationBar(
    scoreCp: Int?,
    mate: Int? = null,
    whiteToMove: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val whiteCp = when {
        mate != null -> if (mate > 0 && whiteToMove || mate < 0 && !whiteToMove) 10000 else -10000
        scoreCp != null -> if (whiteToMove) scoreCp else -scoreCp
        else -> 0
    }
    val target = (100.0 / (1.0 + kotlin.math.exp(-0.00368208 * whiteCp.coerceIn(-10000, 10000)))).toFloat()
    val animated = remember { Animatable(target.coerceIn(0f, 100f)) }
    LaunchedEffect(target) {
        animated.animateTo(target.coerceIn(0f, 100f), tween(420))
    }
    val whiteShare = animated.value / 100f

    Box(
        modifier
            .width(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0B0A10)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF37323F), Color(0xFF14121C))),
                size = Size(size.width, size.height),
            )
            val whiteHeight = size.height * whiteShare
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFFDF5), Color(0xFFD8D2C4))),
                topLeft = Offset(0f, size.height - whiteHeight),
                size = Size(size.width, whiteHeight),
            )
        }
        val label = when {
            mate != null -> "M${kotlin.math.abs(mate)}"
            scoreCp != null -> {
                val value = whiteCp / 100f
                if (value > 0) "+${String.format("%.1f", value)}" else String.format("%.1f", value)
            }
            else -> "0.0"
        }
        Text(
            label,
            color = if (whiteShare > 0.5f) Color(0xFF1A1720) else Color(0xFFD6CFE4),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun CentipawnEvaluationBar(cp: Int?, mate: Int?, whiteToMove: Boolean, modifier: Modifier = Modifier) =
    EvaluationBar(cp, mate, whiteToMove, modifier)
