package com.fredsystems.brilliantboard.domain.chess

/**
 * Compact, dependency-free chess rules engine used by the board UI, the PGN parser and
 * the reviewer. Squares are indexed 0..63 with 0 = a1 and 63 = h8.
 */
data class Position(
    val squares: String,
    val whiteToMove: Boolean = true,
    val castling: String = "KQkq",
    val ep: Int = -1,
    val halfmove: Int = 0,
    val fullmove: Int = 1,
) {
    init {
        require(squares.length == 64) { "A position must have exactly 64 squares" }
    }

    fun pieceAt(index: Int): Char = squares[index]

    fun isOwn(piece: Char): Boolean = piece != '.' && (piece.isUpperCase() == whiteToMove)

    fun isEnemy(piece: Char): Boolean = piece != '.' && (piece.isUpperCase() != whiteToMove)

    fun isCheck(): Boolean {
        val king = if (whiteToMove) 'K' else 'k'
        val kingIndex = squares.indexOf(king)
        return kingIndex >= 0 && isAttacked(kingIndex, !whiteToMove)
    }

    fun isCheckmate(): Boolean = isCheck() && legalMoves().isEmpty()

    fun isStalemate(): Boolean = !isCheck() && legalMoves().isEmpty()

    fun isInsufficientMaterial(): Boolean {
        val pieces = squares.filter { it != '.' }
        if (pieces.count { it.lowercaseChar() == 'p' || it.lowercaseChar() == 'r' || it.lowercaseChar() == 'q' } > 0) {
            return false
        }
        val minors = pieces.filter { it.lowercaseChar() == 'b' || it.lowercaseChar() == 'n' }
        if (minors.isEmpty()) return true
        if (minors.length == 1) return true
        if (minors.length == 2 && minors.all { it.lowercaseChar() == 'b' }) {
            val bishopSquares = squares.mapIndexedNotNull { i, c -> if (c.lowercaseChar() == 'b') i else null }
            return bishopSquares.size == 2 && (bishopSquares[0] + bishopSquares[1]) % 2 == 1
        }
        return false
    }

    fun isAttacked(index: Int, byWhite: Boolean): Boolean {
        val file = index % 8
        val rank = index / 8

        val pawn = if (byWhite) 'P' else 'p'
        val pawnRank = if (byWhite) rank - 1 else rank + 1
        if (pawnRank in 0..7) {
            listOf(file - 1, file + 1).forEach { f ->
                if (f in 0..7 && squares[pawnRank * 8 + f] == pawn) return true
            }
        }

        val knight = if (byWhite) 'N' else 'n'
        KNIGHT_OFFSETS.forEach { (df, dr) ->
            val f = file + df
            val r = rank + dr
            if (f in 0..7 && r in 0..7 && squares[r * 8 + f] == knight) return true
        }

        val king = if (byWhite) 'K' else 'k'
        KING_OFFSETS.forEach { (df, dr) ->
            val f = file + df
            val r = rank + dr
            if (f in 0..7 && r in 0..7 && squares[r * 8 + f] == king) return true
        }

        val bishop = if (byWhite) 'B' else 'b'
        val rook = if (byWhite) 'R' else 'r'
        val queen = if (byWhite) 'Q' else 'q'
        DIAGONAL.forEach { (df, dr) ->
            if (rayHasPiece(file, rank, df, dr, bishop, queen)) return true
        }
        ORTHOGONAL.forEach { (df, dr) ->
            if (rayHasPiece(file, rank, df, dr, rook, queen)) return true
        }
        return false
    }

    private fun rayHasPiece(file: Int, rank: Int, df: Int, dr: Int, vararg targets: Char): Boolean {
        var f = file + df
        var r = rank + dr
        while (f in 0..7 && r in 0..7) {
            val piece = squares[r * 8 + f]
            if (piece != '.') return piece in targets
            f += df
            r += dr
        }
        return false
    }

    fun pseudoLegalMoves(): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        for (index in 0..63) {
            val piece = squares[index]
            if (piece == '.' || piece.isUpperCase() != whiteToMove) continue
            val file = index % 8
            val rank = index / 8
            when (piece.lowercaseChar()) {
                'p' -> pawnMoves(index, file, rank, moves)
                'n' -> leapMoves(index, file, rank, KNIGHT_OFFSETS, moves)
                'k' -> {
                    leapMoves(index, file, rank, KING_OFFSETS, moves)
                    castlingMoves(moves)
                }
                'b' -> slideMoves(index, file, rank, DIAGONAL, moves)
                'r' -> slideMoves(index, file, rank, ORTHOGONAL, moves)
                'q' -> slideMoves(index, file, rank, DIAGONAL + ORTHOGONAL, moves)
            }
        }
        return moves
    }

    private fun pawnMoves(index: Int, file: Int, rank: Int, out: MutableList<ChessMove>) {
        val step = if (whiteToMove) 1 else -1
        val targetRank = rank + step
        if (targetRank !in 0..7) return
        val one = targetRank * 8 + file
        val promotions = if (targetRank == 0 || targetRank == 7) PROMOTIONS else listOf(null)
        if (squares[one] == '.') {
            promotions.forEach { out += ChessMove(index, one, it) }
            val startRank = if (whiteToMove) 1 else 6
            val two = (rank + 2 * step) * 8 + file
            if (rank == startRank && squares[two] == '.') {
                out += ChessMove(index, two, null)
            }
        }
        listOf(file - 1, file + 1).forEach { captureFile ->
            if (captureFile !in 0..7) return@forEach
            val target = targetRank * 8 + captureFile
            if (isEnemy(squares[target]) || target == ep) {
                promotions.forEach { out += ChessMove(index, target, it) }
            }
        }
    }

    private fun leapMoves(
        index: Int,
        file: Int,
        rank: Int,
        offsets: List<Pair<Int, Int>>,
        out: MutableList<ChessMove>,
    ) {
        offsets.forEach { (df, dr) ->
            val f = file + df
            val r = rank + dr
            if (f in 0..7 && r in 0..7) {
                val target = r * 8 + f
                if (!isOwn(squares[target])) out += ChessMove(index, target)
            }
        }
    }

    private fun slideMoves(
        index: Int,
        file: Int,
        rank: Int,
        directions: List<Pair<Int, Int>>,
        out: MutableList<ChessMove>,
    ) {
        directions.forEach { (df, dr) ->
            var f = file + df
            var r = rank + dr
            while (f in 0..7 && r in 0..7) {
                val target = r * 8 + f
                val piece = squares[target]
                if (piece == '.') {
                    out += ChessMove(index, target)
                } else {
                    if (isEnemy(piece)) out += ChessMove(index, target)
                    break
                }
                f += df
                r += dr
            }
        }
    }

    private fun castlingMoves(out: MutableList<ChessMove>) {
        val kingSquare = if (whiteToMove) 4 else 60
        if (squares[kingSquare] != if (whiteToMove) 'K' else 'k') return
        val backRank = if (whiteToMove) 0 else 7
        val enemy = !whiteToMove
        if (isAttacked(kingSquare, enemy)) return

        val kingSide = if (whiteToMove) 'K' else 'k'
        val queenSide = if (whiteToMove) 'Q' else 'q'
        val rook = if (whiteToMove) 'R' else 'r'

        if (kingSide in castling && squares[backRank + 7] == rook &&
            squares[backRank + 5] == '.' && squares[backRank + 6] == '.' &&
            !isAttacked(backRank + 5, enemy) && !isAttacked(backRank + 6, enemy)
        ) {
            out += ChessMove(kingSquare, backRank + 6)
        }
        if (queenSide in castling && squares[backRank] == rook &&
            squares[backRank + 1] == '.' && squares[backRank + 2] == '.' && squares[backRank + 3] == '.' &&
            !isAttacked(backRank + 3, enemy) && !isAttacked(backRank + 2, enemy)
        ) {
            out += ChessMove(kingSquare, backRank + 2)
        }
    }

    fun legalMoves(): List<ChessMove> = pseudoLegalMoves().filter { move ->
        val next = apply(move)
        val king = if (whiteToMove) 'K' else 'k'
        val kingIndex = next.squares.indexOf(king)
        kingIndex < 0 || !next.isAttacked(kingIndex, !whiteToMove)
    }

    fun isLegal(move: ChessMove): Boolean = move in legalMoves()

    fun apply(move: ChessMove): Position {
        val array = squares.toCharArray()
        val piece = array[move.from]
        val captured = array[move.to]
        val isPawn = piece.lowercaseChar() == 'p'
        val isKing = piece.lowercaseChar() == 'k'

        array[move.from] = '.'
        array[move.to] = move.promotion?.let { if (whiteToMove) it.uppercaseChar() else it.lowercaseChar() } ?: piece

        if (isPawn && move.to == ep && captured == '.') {
            val capturedPawnSquare = if (whiteToMove) move.to - 8 else move.to + 8
            array[capturedPawnSquare] = '.'
        }

        if (isKing && kotlin.math.abs(move.to - move.from) == 2) {
            val backRank = if (whiteToMove) 0 else 7
            if (move.to > move.from) {
                array[backRank + 5] = array[backRank + 7]
                array[backRank + 7] = '.'
            } else {
                array[backRank + 3] = array[backRank]
                array[backRank] = '.'
            }
        }

        var nextCastling = castling
        if (isKing) nextCastling = nextCastling.filter { it.isLowerCase() == whiteToMove }
        if (piece == 'R' && move.from == 0) nextCastling = nextCastling.replace("Q", "")
        if (piece == 'R' && move.from == 7) nextCastling = nextCastling.replace("K", "")
        if (piece == 'r' && move.from == 56) nextCastling = nextCastling.replace("q", "")
        if (piece == 'r' && move.from == 63) nextCastling = nextCastling.replace("k", "")
        if (captured == 'R' && move.to == 0) nextCastling = nextCastling.replace("Q", "")
        if (captured == 'R' && move.to == 7) nextCastling = nextCastling.replace("K", "")
        if (captured == 'r' && move.to == 56) nextCastling = nextCastling.replace("q", "")
        if (captured == 'r' && move.to == 63) nextCastling = nextCastling.replace("k", "")
        if (nextCastling.isEmpty()) nextCastling = "-"

        val nextEp = if (isPawn && kotlin.math.abs(move.to - move.from) == 16) {
            (move.from + move.to) / 2
        } else -1

        val nextHalfmove = if (isPawn || captured != '.') 0 else halfmove + 1
        val nextFullmove = fullmove + if (whiteToMove) 0 else 1

        return copy(
            squares = String(array),
            whiteToMove = !whiteToMove,
            castling = nextCastling,
            ep = nextEp,
            halfmove = nextHalfmove,
            fullmove = nextFullmove,
        )
    }

    fun fen(): String {
        val rows = mutableListOf<String>()
        for (rank in 7 downTo 0) {
            val row = StringBuilder()
            var empty = 0
            for (file in 0..7) {
                val piece = squares[rank * 8 + file]
                if (piece == '.') {
                    empty++
                } else {
                    if (empty > 0) { row.append(empty); empty = 0 }
                    row.append(piece)
                }
            }
            if (empty > 0) row.append(empty)
            rows += row.toString()
        }
        val epName = if (ep >= 0) squareName(ep) else "-"
        return "${rows.joinToString("/")} ${if (whiteToMove) "w" else "b"} " +
            "$castling $epName $halfmove $fullmove"
    }

    /** Returns the SAN for a legal move in this position. */
    fun san(move: ChessMove): String {
        val piece = squares[move.from]
        val base = when (piece.lowercaseChar()) {
            'p' -> ""
            'n' -> "N"; 'b' -> "B"; 'r' -> "R"; 'q' -> "Q"; 'k' -> "K"
            else -> ""
        }
        val result = StringBuilder()
        if (piece.lowercaseChar() == 'k' && kotlin.math.abs(move.to - move.from) == 2) {
            result.append(if (move.to > move.from) "O-O" else "O-O-O")
        } else {
            result.append(base)
            val targetPiece = squares[move.to]
            val isEp = piece.lowercaseChar() == 'p' && move.to == ep && targetPiece == '.'
            val isPawnCapture = piece.lowercaseChar() == 'p' && (targetPiece != '.' || isEp)
            val ambiguous = legalMoves().any { other ->
                other != move && other.to == move.to && squares[other.from] == piece
            }
            when {
                isPawnCapture -> result.append(fileChar(move.from))
                ambiguous && piece.lowercaseChar() != 'p' -> {
                    val sameFile = legalMoves().any { other ->
                        other != move && other.to == move.to && squares[other.from] == piece &&
                            other.from % 8 == move.from % 8
                    }
                    val sameRank = legalMoves().any { other ->
                        other != move && other.to == move.to && squares[other.from] == piece &&
                            other.from / 8 == move.from / 8
                    }
                    if (!sameFile) result.append(fileChar(move.from))
                    else if (!sameRank) result.append((move.from / 8 + 1).toString())
                    else {
                        result.append(fileChar(move.from))
                        result.append((move.from / 8 + 1).toString())
                    }
                }
            }
            if (targetPiece != '.' || isEp) result.append("x")
            result.append(squareName(move.to))
            move.promotion?.let { result.append("=").append(it.uppercaseChar()) }
        }
        val next = apply(move)
        if (next.isCheck()) result.append(if (next.isCheckmate()) "#" else "+")
        return result.toString()
    }

    fun parseSan(raw: String): ChessMove? {
        val cleaned = raw.trim()
            .replace("0-0-0", "O-O-O")
            .replace("0-0", "O-O")
            .replace("e.p.", "")
            .replace(Regex("[!?]+\\s*$"), "")
            .replace(Regex("[+#]+\\s*$"), "")
            .trim()
        return legalMoves().firstOrNull {
            san(it).replace(Regex("[+#]+$"), "") == cleaned
        }
    }

    fun parseUci(raw: String): ChessMove? {
        if (raw.length !in 2..5) return null
        val from = squareIndex(raw.substring(0, 2)) ?: return null
        val to = squareIndex(raw.substring(2, 4)) ?: return null
        val promotion = raw.getOrNull(4)?.lowercaseChar()?.takeIf { it in "qrbn" }
        return ChessMove(from, to, promotion)
    }

    companion object {
        val START: Position = fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

        val EMPTY: Position = fromFen("8/8/8/8/8/8/8/8 w - - 0 1")

        private val PROMOTIONS = listOf('q', 'r', 'b', 'n')

        private val KNIGHT_OFFSETS = listOf(1 to 2, 2 to 1, -1 to 2, -2 to 1, 1 to -2, 2 to -1, -1 to -2, -2 to -1)
        private val KING_OFFSETS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val DIAGONAL = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val ORTHOGONAL = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 4) { "Invalid FEN: $fen" }
            val board = CharArray(64) { '.' }
            val ranks = parts[0].split('/')
            require(ranks.size == 8) { "Invalid FEN ranks: $fen" }
            ranks.forEachIndexed { index, row ->
                val rank = 7 - index
                var file = 0
                row.forEach { c ->
                    if (c.isDigit()) {
                        file += c.toString().toInt()
                    } else {
                        require(file < 8) { "Invalid FEN row: $row" }
                        board[rank * 8 + file] = c
                        file++
                    }
                }
            }
            return Position(
                squares = String(board),
                whiteToMove = parts[1] != "b",
                castling = parts[2].takeIf { it != "-" } ?: "-",
                ep = if (parts[3] == "-") -1 else squareIndex(parts[3]) ?: -1,
                halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1,
            )
        }

        fun squareName(index: Int): String = "${fileChar(index)}${index / 8 + 1}"

        fun fileChar(index: Int): Char = ('a'.code + index % 8).toChar()

        fun squareIndex(name: String): Int? {
            if (name.length != 2) return null
            val file = name[0].lowercaseChar() - 'a'
            val rank = name[1].digitToIntOrNull()?.minus(1) ?: return null
            return if (file in 0..7 && rank in 0..7) rank * 8 + file else null
        }
    }
}

data class ChessMove(val from: Int, val to: Int, val promotion: Char? = null) {
    val uci: String get() = Position.squareName(from) + Position.squareName(to) + (promotion?.lowercaseChar()?.toString() ?: "")
}
