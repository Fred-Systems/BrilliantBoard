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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun EvaluationBar(winPercent: Float, modifier: Modifier = Modifier) {
    val target = if (winPercent.isFinite()) winPercent.coerceIn(0f, 100f) else 50f
    val animated = remember { Animatable(target) }
    LaunchedEffect(target) {
        animated.animateTo(target, tween(350))
    }
    val whiteShare = animated.value / 100f
    Box(
        modifier
            .width(20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0A10)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF2B2838), Color(0xFF14121C))),
                size = size,
            )
            val whiteHeight = size.height * whiteShare
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFF4F1EA), Color(0xFFD8D2C4))),
                topLeft = Offset(0f, size.height - whiteHeight),
                size = androidx.compose.ui.geometry.Size(size.width, whiteHeight),
            )
        }
        Text(
            text = if (abs(winPercent - 50f) < 0.5f) "=" else "${winPercent.toInt()}",
            color = Color(0xFFB8B3C7),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun CentipawnEvaluationBar(cp: Int?, mate: Int?, modifier: Modifier = Modifier) {
    val win = when {
        mate != null -> if (mate > 0) 99f else 1f
        cp != null -> 100.0 / (1.0 + kotlin.math.exp(-0.00368208 * cp.coerceIn(-10000, 10000)))
        else -> 50.0
    }.toFloat()
    EvaluationBar(win, modifier)
}

@Suppress("unused")
private val unusedOffset = Offset.Zero
