package com.fredsystems.brilliantboard.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredsystems.brilliantboard.domain.scouting.Wdl
import com.fredsystems.brilliantboard.ui.theme.Accent
import com.fredsystems.brilliantboard.ui.theme.Danger
import com.fredsystems.brilliantboard.ui.theme.Good
import com.fredsystems.brilliantboard.ui.theme.SurfaceRaised
import com.fredsystems.brilliantboard.ui.theme.Warning
import kotlin.math.max
import kotlin.math.min

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, color: Color = Accent, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(Color(0xFF141A20), RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = MaterialTheme.typography.bodySmall.color, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun WdlBar(wdl: Wdl, modifier: Modifier = Modifier) {
    val total = wdl.total.coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(10.dp)) {
        Box(Modifier.weight(wdl.wins / total.toFloat()).background(Good))
        Box(Modifier.weight(wdl.draws / total.toFloat()).background(Color(0xFF9AA0A6)))
        Box(Modifier.weight(wdl.losses / total.toFloat()).background(Danger))
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${wdl.wins}W", color = Good, fontSize = 10.sp)
        Text("${wdl.draws}D", color = Color(0xFF9AA0A6), fontSize = 10.sp)
        Text("${wdl.losses}L", color = Danger, fontSize = 10.sp)
    }
}

@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    minValue: Float? = null,
    maxValue: Float? = null,
) {
    val allValues = series.flatMap { it.values }.filter { !it.isNaN() }
    if (allValues.isEmpty()) {
        Box(modifier.height(120.dp), contentAlignment = Alignment.Center) {
            Text("No data yet", color = Color(0xFF6B7280), fontSize = 12.sp)
        }
        return
    }
    val low = minValue ?: (allValues.minOrNull() ?: 0f)
    val high = maxValue ?: (allValues.maxOrNull() ?: 1f)
    val span = max(0.001f, high - low)

    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        drawRect(Color(0xFF111820), size = Size(size.width, size.height))
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(Color(0xFF2A3340), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        series.forEach { line ->
            val points = line.values.mapIndexedNotNull { index, value ->
                if (value.isNaN()) null else {
                    val x = size.width * index / max(1, line.values.size - 1).toFloat()
                    val y = size.height - (value - low) / span * size.height * 0.9f - size.height * 0.05f
                    Offset(x, y)
                }
            }
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, line.color.copy(alpha = 0.25f), style = Stroke(width = 10f))
                drawPath(path, line.color, style = Stroke(width = 3f))
                points.forEach { drawCircle(line.color, radius = 3.5f, center = it) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        series.forEach {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(it.color, RoundedCornerShape(2.dp)))
                Spacer(Modifier.size(4.dp))
                Text(it.label, fontSize = 10.sp, color = Color(0xFF9AA0A6))
            }
        }
    }
}

data class ChartSeries(val label: String, val values: List<Float>, val color: Color)

@Composable
fun BarChart(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier) {
    val finiteValues = values.map { if (it.isNaN()) 0f else it }
    val maxValue = max(1f, finiteValues.maxOrNull() ?: 1f)
    Canvas(modifier.fillMaxWidth().height(130.dp)) {
        val barWidth = size.width / max(1, finiteValues.size) * 0.7f
        finiteValues.forEachIndexed { index, value ->
            val x = size.width * index / max(1, finiteValues.size) + (size.width / max(1, finiteValues.size) - barWidth) / 2
            val height = (value / maxValue * size.height * 0.85f)
            drawRect(
                color = Accent,
                topLeft = Offset(x, size.height - height),
                size = Size(barWidth, height),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        labels.forEach { Text(it, fontSize = 9.sp, color = Color(0xFF9AA0A6), textAlign = TextAlign.Center) }
    }
}

@Composable
fun Heatmap(values: List<Float>, columns: Int, modifier: Modifier = Modifier) {
    val rows = (values.size + columns - 1) / columns
    Canvas(modifier.fillMaxWidth().height((rows * 22).dp)) {
        val cellW = size.width / columns
        val cellH = size.height / max(1, rows)
        values.forEachIndexed { index, value ->
            val x = (index % columns) * cellW
            val y = (index / columns) * cellH
            val color = if (value.isNaN()) Color(0xFF1B222B) else when {
                value >= 60f -> Good
                value >= 45f -> Warning
                else -> Danger
            }
            drawRoundRect(
                color = color.copy(alpha = if (value.isNaN()) 0.5f else 0.85f),
                topLeft = Offset(x + 2, y + 2),
                size = Size(cellW - 4, cellH - 4),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
        }
    }
}

@Composable
fun ProgressRow(label: String, value: Float, color: Color = Accent) {
    val safe = if (value.isFinite()) value.coerceIn(0f, 100f) else 0f
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.fillMaxWidth(0.45f), fontSize = 12.sp, color = MaterialTheme.typography.bodySmall.color)
        Box(Modifier.weight(1f).height(8.dp).background(Color(0xFF101820), RoundedCornerShape(4.dp))) {
            Box(
                Modifier.fillMaxWidth(min(1f, safe / 100f)).height(8.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
        Text("${safe.toInt()}%", fontSize = 11.sp, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Keeps an unexpected composition issue from taking down the whole app. */
@Composable
fun SafeContent(content: @Composable () -> Unit) {
    val failure = runCatching { content() }.exceptionOrNull()
    if (failure != null) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("This screen could not be displayed", color = Danger, fontWeight = FontWeight.Bold)
                Text(failure.message ?: failure.javaClass.simpleName, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun EmptyState(message: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = Color(0xFF9AA0A6), textAlign = TextAlign.Center)
        action?.invoke()
    }
}
