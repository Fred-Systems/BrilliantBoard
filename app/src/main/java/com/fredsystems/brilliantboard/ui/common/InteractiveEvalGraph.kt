package com.fredsystems.brilliantboard.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredsystems.brilliantboard.domain.model.EvalPoint
import com.fredsystems.brilliantboard.ui.theme.Accent

/** Evaluation curve that scrubs the board while the user drags a finger across it. */
@Composable
fun InteractiveEvalGraph(points: List<EvalPoint>, currentPly: Int, onScrub: (Int) -> Unit) {
    if (points.isEmpty()) {
        Text("Analyse the game to see the evaluation curve", fontSize = 12.sp, color = Color(0xFF9AA0A6))
        return
    }
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(points.size) {
                    detectTapGestures { offset ->
                        val index = (offset.x / size.width * points.lastIndex).toInt()
                            .coerceIn(0, points.lastIndex)
                        onScrub(points[index].ply)
                    }
                }
                .pointerInput(points.size) {
                    detectHorizontalDragGestures { change, _ ->
                        val index = (change.position.x / size.width * points.lastIndex).toInt()
                            .coerceIn(0, points.lastIndex)
                        onScrub(points[index].ply)
                    }
                },
        ) {
            drawRect(Color(0xFF111820), size = Size(size.width, size.height))
            for (i in 0..2) {
                val y = size.height * i / 2f
                drawLine(Color(0xFF2A3340), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = size.width * index / points.lastIndex.coerceAtLeast(1).toFloat()
                val y = size.height - (point.winPercent / 100f) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Accent.copy(alpha = 0.3f), style = Stroke(8f))
            drawPath(path, Accent, style = Stroke(2.5f))

            val cursorIndex = points.indexOfFirst { it.ply >= currentPly }.coerceAtLeast(0)
            val cursorX = size.width * cursorIndex / points.lastIndex.coerceAtLeast(1).toFloat()
            drawLine(Accent, Offset(cursorX, 0f), Offset(cursorX, size.height), strokeWidth = 2f)
        }
        Text("Drag across the graph to jump to any move", fontSize = 10.sp, color = Color(0xFF8A93A0))
    }
}
