package com.fredsystems.brilliantboard.ui.board

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.fredsystems.brilliantboard.R
import com.fredsystems.brilliantboard.domain.chess.ChessMove
import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.ui.theme.BoardTheme
import com.fredsystems.brilliantboard.ui.theme.BoardThemes
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val SelectedGlow = Color(0xFF7C4DFF)
private val LegalDot = Color(0x662B1A0E)
private val LegalCapture = Color(0x552B1A0E)
private val LastMoveTint = Color(0x66F4C95D)
private val CheckTint = Color(0x99E53935)
private val BoardFrame = Color(0xFF17131F)

data class BoardArrow(val from: Int, val to: Int, val color: Color)

private object PieceBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(160)

    fun get(set: String, code: String, load: () -> ImageBitmap?): ImageBitmap? {
        val key = "$set/$code"
        cache.get(key)?.let { return it }
        val bitmap = load() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}

/**
 * Aetheris-style board: raster Lichess piece sets, 25 board colour themes, last-move and
 * check highlights, legal dots/rings, coordinate labels, slide animation, tap and drag
 * input, plus multi-colour analysis arrows.
 */
@Composable
fun ChessBoard(
    position: Position,
    modifier: Modifier = Modifier,
    pieceSet: String = "cburnett",
    theme: BoardTheme = BoardThemes.byId("brown"),
    flipped: Boolean = false,
    lastMove: Pair<Int, Int>? = null,
    selected: Int? = null,
    arrows: List<BoardArrow> = emptyList(),
    showLegalMoves: Boolean = true,
    boardSize: Dp = 340.dp,
    onSquareClick: (Int) -> Unit = {},
    onMove: (ChessMove) -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val animation = remember(lastMove?.first, lastMove?.second) { Animatable(0f) }
    var internalSelected by remember { mutableStateOf<Int?>(null) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var draggedMove by remember { mutableStateOf(false) }
    var boardWidthPx by remember { mutableStateOf(0) }

    val activeSelected = selected ?: internalSelected
    val legalMoves = remember(position, activeSelected) {
        if (activeSelected == null) emptyList() else position.legalMoves().filter { it.from == activeSelected }
    }
    val checkSquare = remember(position) {
        val king = if (position.whiteToMove) 'K' else 'k'
        position.squares.indexOf(king).takeIf { it >= 0 && position.isCheck() }
    }

    LaunchedEffect(lastMove?.first, lastMove?.second) {
        internalSelected = null
        if (lastMove != null && !draggedMove) {
            animation.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        } else {
            animation.snapTo(1f)
        }
        draggedMove = false
    }

    fun displayToIndex(row: Int, col: Int): Int =
        if (!flipped) (7 - row) * 8 + col else row * 8 + (7 - col)

    fun indexAt(point: Offset, widthPx: Int): Int? {
        if (widthPx <= 0 || point.x < 0f || point.y < 0f || point.x >= widthPx || point.y >= widthPx) return null
        val cell = widthPx / 8f
        return displayToIndex((point.y / cell).toInt().coerceIn(0, 7), (point.x / cell).toInt().coerceIn(0, 7))
    }

    fun handleSquare(index: Int) {
        val piece = position.pieceAt(index)
        val ownPiece = piece != '.' && piece.isUpperCase() == position.whiteToMove
        if (activeSelected != null && index != activeSelected) {
            val move = legalMoves.firstOrNull { it.to == index }
            if (move != null) {
                onMove(move)
                internalSelected = null
                onSquareClick(index)
                return
            }
            internalSelected = if (ownPiece) index else null
        } else {
            internalSelected = if (ownPiece) index else null
        }
        onSquareClick(index)
    }

    val currentHandle = rememberUpdatedState { index: Int -> handleSquare(index) }
    val currentPosition = rememberUpdatedState { position }

    BoxWithConstraints(
        modifier = modifier
            .size(boardSize)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(BoardFrame)
            .onSizeChanged { boardWidthPx = it.width }
            .pointerInput(flipped, position, lastMove) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = indexAt(down.position, size.width) ?: return@awaitEachGesture
                    dragFrom = start
                    dragPosition = down.position
                    dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        dragPosition = change.position
                        if (!change.pressed) {
                            val target = indexAt(change.position, size.width)
                            if (dragging && target != null && target != start) {
                                val move = currentPosition.value().legalMoves().firstOrNull {
                                    it.from == start && it.to == target
                                }
                                if (move != null) {
                                    draggedMove = true
                                    onMove(move)
                                } else {
                                    currentHandle.value(start)
                                }
                            } else {
                                currentHandle.value(start)
                            }
                            break
                        }
                        val movedEnough = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        if (!dragging && movedEnough) {
                            val piece = position.pieceAt(start)
                            if (piece != '.' && piece.isUpperCase() == position.whiteToMove) {
                                dragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                change.consume()
                            }
                        } else if (dragging) {
                            change.consume()
                        }
                    }
                    dragFrom = null
                    dragging = false
                }
            },
    ) {
        val square = maxWidth / 8
        val squarePx = with(density) { square.toPx() }
        val animating = lastMove != null && animation.value < 1f
        val light = theme.light
        val dark = theme.dark

        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / 8f
            for (row in 0..7) {
                for (col in 0..7) {
                    drawRect(
                        color = if ((row + col) % 2 == 0) light else dark,
                        topLeft = Offset(col * cell, row * cell),
                        size = Size(cell, cell),
                    )
                }
            }
            for (row in 0..7) {
                for (col in 0..7) {
                    val index = displayToIndex(row, col)
                    val topLeft = Offset(col * cell, row * cell)
                    val full = Size(cell, cell)
                    if (lastMove != null && (index == lastMove.first || index == lastMove.second)) {
                        drawRect(LastMoveTint, topLeft, full)
                    }
                    if (index == activeSelected) {
                        drawRect(SelectedGlow.copy(alpha = 0.45f), topLeft, full)
                    }
                    if (index == checkSquare) {
                        val center = Offset(topLeft.x + cell / 2, topLeft.y + cell / 2)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(CheckTint, Color.Transparent),
                                center = center,
                                radius = cell * 0.7f,
                            ),
                            radius = cell * 0.7f,
                            center = center,
                        )
                    }
                    if (showLegalMoves && index in legalMoves.map { it.to }) {
                        val center = Offset(topLeft.x + cell / 2, topLeft.y + cell / 2)
                        if (position.pieceAt(index) != '.') {
                            drawCircle(LegalCapture, radius = cell * 0.42f, center = center, style = Stroke(cell * 0.09f))
                        } else {
                            drawCircle(LegalDot, radius = cell * 0.14f, center = center)
                        }
                    }
                }
            }
            arrows.forEach { arrow ->
                val start = displayOffset(arrow.from, cell, flipped)
                val end = displayOffset(arrow.to, cell, flipped)
                drawLine(arrow.color, start, end, strokeWidth = cell * 0.14f)
                val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
                val head = cell * 0.32f
                val path = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo(end.x - head * cos(angle - 0.5).toFloat(), end.y - head * sin(angle - 0.5).toFloat())
                    lineTo(end.x - head * cos(angle + 0.5).toFloat(), end.y - head * sin(angle + 0.5).toFloat())
                    close()
                }
                drawPath(path, arrow.color)
            }
        }

        for (row in 0..7) {
            for (col in 0..7) {
                val index = displayToIndex(row, col)
                val piece = position.pieceAt(index)
                val isDragged = dragging && index == dragFrom
                if (piece != '.' && !isDragged && !(animating && index == lastMove?.second)) {
                    PieceImage(
                        piece = piece,
                        pieceSet = pieceSet,
                        modifier = Modifier
                            .offset(x = square * col, y = square * row)
                            .size(square)
                            .padding(square * 0.045f),
                    )
                }
                val labelColor = if ((row + col) % 2 == 0) Color(0x993B2A1A) else Color(0x99F6E7D2)
                if (row == 7) {
                    Text(
                        ('a' + index % 8).toString(),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = square * col, y = square * row)
                            .size(square)
                            .padding(2.dp),
                        color = labelColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (col == 0) {
                    Text(
                        "${index / 8 + 1}",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = square * col, y = square * row)
                            .size(square)
                            .padding(2.dp),
                        color = labelColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }

        if (animating && lastMove != null) {
            val from = displayOffset(lastMove.first, squarePx, flipped)
            val to = displayOffset(lastMove.second, squarePx, flipped)
            val movingPiece = position.pieceAt(lastMove.second)
            if (movingPiece != '.') {
                val x = from.x + (to.x - from.x) * animation.value
                val y = from.y + (to.y - from.y) * animation.value
                PieceImage(
                    piece = movingPiece,
                    pieceSet = pieceSet,
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .size(square)
                        .padding(square * 0.045f)
                        .zIndex(3f),
                )
            }
        }

        if (dragging && dragFrom != null) {
            val piece = position.pieceAt(dragFrom!!)
            if (piece != '.') {
                PieceImage(
                    piece = piece,
                    pieceSet = pieceSet,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (dragPosition.x - squarePx / 2f).roundToInt(),
                                (dragPosition.y - squarePx / 2f).roundToInt(),
                            )
                        }
                        .size(square)
                        .padding(square * 0.02f)
                        .zIndex(5f),
                )
            }
        }
    }
}

private fun displayOffset(index: Int, cell: Float, flipped: Boolean): Offset {
    val file = index % 8
    val rank = index / 8
    return if (!flipped) Offset(file * cell, (7 - rank) * cell) else Offset((7 - file) * cell, rank * cell)
}

@Composable
fun PieceImage(piece: Char, pieceSet: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val code = "${if (piece.isUpperCase()) 'w' else 'b'}${piece.uppercaseChar()}"
    val bitmap = remember(pieceSet, code) {
        PieceBitmapCache.get(pieceSet, code) {
            runCatching {
                context.assets.open("pieces/$pieceSet/$code.png").use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier, filterQuality = FilterQuality.High)
    } else {
        val glyph = when (piece.uppercaseChar()) {
            'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"; else -> ""
        }
        Text(
            glyph,
            modifier = modifier,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
            color = if (piece.isUpperCase()) Color(0xFFFFFDF5) else Color(0xFF1A1A1A),
        )
    }
}

@Composable
fun BoardWithCoords(
    position: Position,
    modifier: Modifier = Modifier,
    pieceSet: String = "cburnett",
    theme: BoardTheme = BoardThemes.byId("brown"),
    flipped: Boolean = false,
    lastMove: Pair<Int, Int>? = null,
    selected: Int? = null,
    arrows: List<BoardArrow> = emptyList(),
    showLegalMoves: Boolean = true,
    boardSize: Dp = 340.dp,
    onSquareClick: (Int) -> Unit = {},
    onMove: (ChessMove) -> Unit = {},
) {
    ChessBoard(
        position = position,
        modifier = modifier,
        pieceSet = pieceSet,
        theme = theme,
        flipped = flipped,
        lastMove = lastMove,
        selected = selected,
        arrows = arrows,
        showLegalMoves = showLegalMoves,
        boardSize = boardSize,
        onSquareClick = onSquareClick,
        onMove = onMove,
    )
}

@Suppress("unused")
private val fallbackDrawables = listOf(
    R.drawable.piece_wk, R.drawable.piece_wq, R.drawable.piece_wr, R.drawable.piece_wb,
    R.drawable.piece_wn, R.drawable.piece_wp, R.drawable.piece_bk, R.drawable.piece_bq,
    R.drawable.piece_br, R.drawable.piece_bb, R.drawable.piece_bn, R.drawable.piece_bp,
)
