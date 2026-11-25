package com.fredsystems.brilliantboard.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredsystems.brilliantboard.domain.chess.ChessMove
import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.ui.theme.BoardTheme
import kotlinx.coroutines.launch

data class BoardArrow(val from: Int, val to: Int, val color: Color)

private val GLYPHS = mapOf(
    'K' to "♔", 'Q' to "♕", 'R' to "♖", 'B' to "♗", 'N' to "♘", 'P' to "♙",
    'k' to "♚", 'q' to "♛", 'r' to "♜", 'b' to "♝", 'n' to "♞", 'p' to "♟",
)

@Composable
fun ChessBoard(
    position: Position,
    modifier: Modifier = Modifier,
    theme: BoardTheme = BoardTheme.EMERALD,
    flipped: Boolean = false,
    lastMove: Pair<Int, Int>? = null,
    selected: Int? = null,
    arrows: List<BoardArrow> = emptyList(),
    showLegalMoves: Boolean = true,
    boardSize: Dp = 340.dp,
    onSquareClick: (Int) -> Unit = {},
    onMove: (ChessMove) -> Unit = {},
) {
    val legalTargets = remember(selected, position) {
        if (selected == null) emptyList() else position.legalMoves().filter { it.from == selected }
    }
    val cell = boardSize / 8
    val density = LocalDensity.current
    val cellPx = with(density) { cell.toPx() }
    val slide = remember { Animatable(1f) }

    LaunchedEffect(lastMove?.first, lastMove?.second) {
        if (lastMove != null) {
            slide.snapTo(0f)
            launch { slide.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 280f)) }
        }
    }

    fun displayCoords(index: Int): Pair<Int, Int> {
        val file = index % 8
        val rank = index / 8
        return if (flipped) (7 - file) to rank else file to (7 - rank)
    }

    fun squareAt(x: Float, y: Float): Int? {
        val col = (x / cellPx).toInt().coerceIn(0, 7)
        val row = (y / cellPx).toInt().coerceIn(0, 7)
        val file = if (flipped) 7 - col else col
        val rank = if (flipped) row else 7 - row
        return rank * 8 + file
    }

    Box(modifier.size(boardSize).pointerInput(selected, flipped) {
        detectTapGestures { offset ->
            val target = squareAt(offset.x, offset.y) ?: return@detectTapGestures
            if (selected != null && target != selected) {
                legalTargets.firstOrNull { it.to == target }?.let { onMove(it) }
                    ?: onSquareClick(target)
            } else {
                onSquareClick(target)
            }
        }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val cellPx = size.width / 8f
            for (index in 0..63) {
                val (col, row) = displayCoords(index)
                val isLight = (index % 8 + index / 8) % 2 == 0
                val base = if (isLight) theme.light else theme.dark
                val color = when {
                    selected == index -> base.copy(alpha = 0.75f).copy(red = 0.55f, green = 0.75f, blue = 0.35f)
                    lastMove != null && (index == lastMove.first || index == lastMove.second) -> theme.lastMove
                    else -> base
                }
                drawRect(
                    color = color,
                    topLeft = Offset(col * cellPx, row * cellPx),
                    size = Size(cellPx, cellPx),
                )
            }
            if (selected != null && showLegalMoves) {
                legalTargets.forEach { move ->
                    val (col, row) = displayCoords(move.to)
                    val center = Offset((col + 0.5f) * cellPx, (row + 0.5f) * cellPx)
                    drawCircle(
                        color = Color(0x66000000),
                        radius = cellPx * 0.20f,
                        center = center,
                    )
                }
            }
            arrows.forEach { arrow ->
                val (fromCol, fromRow) = displayCoords(arrow.from)
                val (toCol, toRow) = displayCoords(arrow.to)
                val start = Offset((fromCol + 0.5f) * cellPx, (fromRow + 0.5f) * cellPx)
                val end = Offset((toCol + 0.5f) * cellPx, (toRow + 0.5f) * cellPx)
                drawLine(arrow.color, start, end, strokeWidth = cellPx * 0.14f)
                val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
                val head = cellPx * 0.32f
                val path = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo(
                        end.x - head * kotlin.math.cos(angle - 0.5).toFloat(),
                        end.y - head * kotlin.math.sin(angle - 0.5).toFloat(),
                    )
                    lineTo(
                        end.x - head * kotlin.math.cos(angle + 0.5).toFloat(),
                        end.y - head * kotlin.math.sin(angle + 0.5).toFloat(),
                    )
                    close()
                }
                drawPath(path, arrow.color)
            }
        }

        for (index in 0..63) {
            val piece = position.pieceAt(index)
            if (piece == '.') continue
            val (col, row) = displayCoords(index)
            var extraX = 0.dp
            var extraY = 0.dp
            if (lastMove != null && index == lastMove.second) {
                val (fromCol, fromRow) = displayCoords(lastMove.first)
                val factor = 1f - slide.value
                extraX = ((fromCol - col) * factor).toInt().dp
                extraY = ((fromRow - row) * factor).toInt().dp
            }
            val glyphX: Dp = with(density) { (col * cellPx).toDp() } + extraX
            val glyphY: Dp = with(density) { (row * cellPx).toDp() } + extraY
            Text(
                text = GLYPHS[piece].orEmpty(),
                modifier = Modifier
                    .offset(x = glyphX, y = glyphY)
                    .size(cell),
                fontSize = (cell.value * 0.78f).sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                color = if (piece.isUpperCase()) Color(0xFFFFFDF5) else Color(0xFF1A1A1A),
            )
        }
    }
}

@Composable
fun BoardWithCoords(
    position: Position,
    modifier: Modifier = Modifier,
    theme: BoardTheme = BoardTheme.EMERALD,
    flipped: Boolean = false,
    lastMove: Pair<Int, Int>? = null,
    selected: Int? = null,
    arrows: List<BoardArrow> = emptyList(),
    showLegalMoves: Boolean = true,
    onSquareClick: (Int) -> Unit = {},
    onMove: (ChessMove) -> Unit = {},
) {
    val files = if (flipped) listOf('h', 'g', 'f', 'e', 'd', 'c', 'b', 'a') else ('a'..'h').toList()
    val ranks = if (flipped) (1..8).toList() else (8 downTo 1).toList()
    Row(modifier.padding(4.dp)) {
        Column(Modifier.height(340.dp)) {
            ranks.forEach {
                Text(
                    it.toString(),
                    modifier = Modifier.height(42.5.dp).width(14.dp).padding(top = 10.dp),
                    fontSize = 10.sp,
                    color = Color(0xFF9AA0A6),
                )
            }
        }
        Column {
            ChessBoard(
                position = position,
                theme = theme,
                flipped = flipped,
                lastMove = lastMove,
                selected = selected,
                arrows = arrows,
                showLegalMoves = showLegalMoves,
                onSquareClick = onSquareClick,
                onMove = onMove,
            )
            Row(Modifier.height(16.dp)) {
                files.forEach {
                    Text(
                        it.toString(),
                        modifier = Modifier.width(42.5.dp),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF9AA0A6),
                    )
                }
            }
        }
    }
}
