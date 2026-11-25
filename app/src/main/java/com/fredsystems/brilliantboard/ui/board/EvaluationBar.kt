package com.fredsystems.brilliantboard.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EvaluationBar(winPercent: Float, modifier: Modifier = Modifier) {
    val whiteShare = winPercent.coerceIn(0f, 100f) / 100f
    Box(modifier.width(18.dp).fillMaxHeight()) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Color(0xFF111111), size = Size(size.width, size.height))
            val whiteHeight = size.height * whiteShare
            drawRect(
                Color(0xFFF2F2F2),
                topLeft = Offset(0f, size.height - whiteHeight),
                size = Size(size.width, whiteHeight),
            )
        }
        Text(
            text = "${winPercent.toInt()}",
            color = if (whiteShare > 0.5f) Color.Black else Color.White,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
