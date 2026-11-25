package com.fredsystems.brilliantboard.data.scraper

import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.domain.model.ClockInfo
import com.fredsystems.brilliantboard.domain.model.GameRecord
import com.fredsystems.brilliantboard.domain.model.GameResult
import com.fredsystems.brilliantboard.domain.model.MoveRecord
import com.fredsystems.brilliantboard.domain.model.OpeningInfo
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.domain.model.Side
import com.fredsystems.brilliantboard.domain.model.Termination
import com.fredsystems.brilliantboard.domain.model.TimeClass

/** Universal PGN / clock / evaluation parser for both Chess.com and Lichess exports. */
object PgnParser {

    private val TAG_REGEX = Regex("""\[\s*([A-Za-z0-9_]+)\s+"([^"]*)"\s*\]""")
    private val TOKEN_REGEX = Regex("""\{[^}]*\}|\[[^\]]*\]|\$\d+|[^\s]+""")
    private val CLK_REGEX = Regex("""\[%\s*clk\s+([\d:.]+)\]""", RegexOption.IGNORE_CASE)
    private val EVAL_REGEX = Regex("""\[%\s*eval\s+([^\]]+)\]""", RegexOption.IGNORE_CASE)

    fun parse(
        pgn: String,
        platform: Platform,
        user: String? = null,
        fallbackId: String = "pgn-${pgn.hashCode()}",
    ): GameRecord? {
        val tags = TAG_REGEX.findAll(pgn).associate { it.groupValues[1] to it.groupValues[2] }
        if (tags.isEmpty() && !pgn.contains(Regex("""\b(?:1\.|1\.\.\.)"""))) return null

        val movetextStart = TAG_REGEX.find(pgn)?.range?.endInclusive?.plus(1) ?: 0
        val stripped = stripVariations(pgn.substring(movetextStart))
        val white = tags["White"] ?: "Unknown"
        val black = tags["Black"] ?: "Unknown"
        val userSide = when {
            user.isNullOrBlank() -> Side.WHITE
            white.equals(user, true) -> Side.WHITE
            black.equals(user, true) -> Side.BLACK
            else -> Side.WHITE
        }

        val fen = tags["FEN"]?.takeIf { it != "-" }
        var position = runCatching { fen?.let { Position.fromFen(it) } ?: Position.START }.getOrDefault(Position.START)

        val moves = mutableListOf<MoveRecord>()
        val whiteClocks = mutableListOf<Int>()
        val blackClocks = mutableListOf<Int>()
        val tokens = TOKEN_REGEX.findAll(stripped).map { it.value }.toList()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val ignorable = token.startsWith("{") || token.startsWith("[") || token.startsWith("$") ||
                token.matches(Regex("""\d+\.+""")) ||
                token in setOf("1-0", "0-1", "1/2-1/2", "*")
            if (ignorable) {
                index++
                continue
            }

            val comment = StringBuilder()
            var lookahead = index + 1
            while (lookahead < tokens.size && tokens[lookahead].startsWith("{")) {
                comment.append(tokens[lookahead].trim('{', '}')).append(' ')
                lookahead++
            }

            val sanMove = position.parseSan(token)
            val applied = sanMove ?: position.parseUci(token)?.takeIf { position.isLegal(it) }
            if (applied != null) {
                applyMove(
                    position,
                    applied,
                    if (sanMove != null) position.san(applied) else token,
                    moves,
                    whiteClocks,
                    blackClocks,
                    comment.toString(),
                    ply = moves.size + 1,
                )
                position = position.apply(applied)
            }
            index++
        }

        val timeControl = tags["TimeControl"]
        val parsedClock = parseTimeControl(timeControl)
        val result = GameResult.fromWhiteResult(tags["Result"] ?: inferResult(position, tags))

        return GameRecord(
            id = tags["Site"]?.takeIf { it.length < 120 }?.let { "$platform:$it" }
                ?: tags["GameId"]?.let { "$platform:$it" } ?: fallbackId,
            platform = platform,
            white = white,
            black = black,
            whiteElo = tags["WhiteElo"]?.toIntOrNull(),
            blackElo = tags["BlackElo"]?.toIntOrNull(),
            userSide = userSide,
            result = result,
            termination = Termination.parse(tags["Termination"] ?: tags["Result"]),
            playedAt = parseDate(tags["Date"] ?: tags["UTCDate"]),
            timeClass = TimeClass.from(tags["TimeClass"] ?: timeClassFromControl(timeControl)),
            timeControl = timeControl,
            rated = tags["Rated"]?.equals("true", true) ?: (tags["RatingType"] != "casual"),
            opening = OpeningInfo(
                eco = tags["ECO"]?.takeIf { it.isNotBlank() },
                name = tags["Opening"]?.takeIf { it.isNotBlank() },
                moves = tags["OpeningMoves"]?.takeIf { it.isNotBlank() },
                ply = moves.size,
            ),
            clocks = ClockInfo(
                initialSeconds = parsedClock?.first,
                incrementSeconds = parsedClock?.second,
                whiteClocks = whiteClocks,
                blackClocks = blackClocks,
            ),
            moves = moves,
            pgn = pgn,
        )
    }

    private fun applyMove(
        before: Position,
        move: com.fredsystems.brilliantboard.domain.chess.ChessMove,
        san: String,
        output: MutableList<MoveRecord>,
        whiteClocks: MutableList<Int>,
        blackClocks: MutableList<Int>,
        comment: String,
        ply: Int,
    ) {
        val after = before.apply(move)
        val moverIsWhite = before.whiteToMove
        val captured = before.pieceAt(move.to) != '.'
        val isEp = before.pieceAt(move.from).lowercaseChar() == 'p' &&
            move.to == before.ep && !captured
        val materialBefore = material(before.squares, moverIsWhite)
        val materialAfter = material(after.squares, moverIsWhite)
        val isSacrifice = !captured && !isEp && materialAfter <= materialBefore - 250

        val clock = CLK_REGEX.find(comment)?.groupValues?.get(1)?.let { clockToSeconds(it) }
        if (clock != null) {
            if (moverIsWhite) whiteClocks += clock else blackClocks += clock
        }
        val evalText = EVAL_REGEX.find(comment)?.groupValues?.get(1)?.trim()
        val mate = evalText?.removePrefix("#")?.toIntOrNull()?.takeIf { evalText.startsWith("#") }
        val cp = if (evalText != null && mate == null) {
            runCatching { (evalText.toFloat() * 100).toInt() }.getOrNull()
        } else null

        output += MoveRecord(
            ply = ply,
            san = san,
            uci = move.uci,
            fen = after.fen(),
            clockSeconds = clock,
            evalCp = cp,
            mate = mate,
            isCapture = captured || isEp,
            isCheck = after.isCheck(),
            isSacrifice = isSacrifice,
            side = if (moverIsWhite) Side.WHITE else Side.BLACK,
        )
    }

    private fun material(squares: String, forWhite: Boolean): Int {
        val values = mapOf('p' to 100, 'n' to 320, 'b' to 330, 'r' to 500, 'q' to 900)
        return squares.sumOf { piece ->
            if (piece == '.') 0 else if (piece.isUpperCase() == forWhite) values[piece.lowercaseChar()] ?: 0 else 0
        }
    }

    private fun stripVariations(text: String): String {
        val result = StringBuilder()
        var depth = 0
        text.forEach { char ->
            when (char) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                else -> if (depth == 0) result.append(char)
            }
        }
        return result.toString()
    }

    private fun inferResult(position: Position, tags: Map<String, String>): String {
        return when {
            position.isCheckmate() -> if (position.whiteToMove) "0-1" else "1-0"
            position.isStalemate() || position.isInsufficientMaterial() -> "1/2-1/2"
            tags["Result"] == "*" -> "1/2-1/2"
            else -> "1/2-1/2"
        }
    }

    fun clockToSeconds(value: String): Int? {
        val parts = value.trim().split(":")
        return runCatching {
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toFloat().toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toFloat().toInt()
                else -> null
            }
        }.getOrNull()
    }

    fun parseTimeControl(value: String?): Pair<Int, Int>? {
        if (value.isNullOrBlank()) return null
        val base = value.substringBefore("/")
        val parts = base.split("+")
        val initial = parseDuration(parts[0]) ?: return null
        val increment = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return initial to increment
    }

    private fun parseDuration(value: String): Int? {
        val number = value.toIntOrNull() ?: return null
        return when {
            value.contains("day", true) -> number * 86400
            else -> number
        }
    }

    fun timeClassFromControl(control: String?): String = when {
        control == null -> "unknown"
        control.contains("day", true) || control.startsWith("1/") -> "daily"
        parseTimeControl(control)?.first?.let { it < 180 } == true -> "bullet"
        parseTimeControl(control)?.first?.let { it < 600 } == true -> "blitz"
        else -> "rapid"
    }

    fun parseDate(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        val normalized = value.replace('.', '-')
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(normalized)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }
}
