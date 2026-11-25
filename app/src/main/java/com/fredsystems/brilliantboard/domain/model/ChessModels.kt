package com.fredsystems.brilliantboard.domain.model

/** Platform a game/account belongs to. */
enum class Platform(val label: String) {
    CHESS_COM("Chess.com"),
    LICHESS("Lichess");

    companion object {
        fun from(value: String?): Platform = when (value?.lowercase()) {
            "lichess", "lila" -> LICHESS
            else -> CHESS_COM
        }
    }
}

enum class Side(val label: String) {
    WHITE("White"),
    BLACK("Black");

    fun opposite(): Side = if (this == WHITE) BLACK else WHITE

    companion object {
        fun from(value: String?): Side =
            if (value?.contains("black", ignoreCase = true) == true) BLACK else WHITE
    }
}

enum class TimeClass(val label: String) {
    BULLET("Bullet"),
    BLITZ("Blitz"),
    RAPID("Rapid"),
    DAILY("Daily"),
    UNKNOWN("Unknown");

    companion object {
        fun from(value: String?): TimeClass = when (value?.lowercase()) {
            "bullet" -> BULLET
            "blitz" -> BLITZ
            "rapid", "standard", "classical" -> RAPID
            "daily", "correspondence" -> DAILY
            else -> UNKNOWN
        }
    }
}

enum class GameResult { WIN, DRAW, LOSS;

    val score: Float get() = when (this) { WIN -> 1f; DRAW -> 0.5f; LOSS -> 0f }

    companion object {
        fun fromWhiteResult(value: String?): GameResult = when (value?.trim()) {
            "1-0" -> GameResult.WIN
            "0-1" -> GameResult.LOSS
            else -> GameResult.DRAW
        }

        fun forSide(whiteResult: String?, side: Side): GameResult {
            val white = fromWhiteResult(whiteResult)
            return if (side == Side.WHITE) white else when (white) {
                WIN -> LOSS
                LOSS -> WIN
                DRAW -> DRAW
            }
        }
    }
}

enum class Termination {
    CHECKMATE, TIMEOUT, RESIGNATION, ABANDONMENT, AGREEMENT, STALEMATE,
    INSUFFICIENT_MATERIAL, FIFTY_MOVE, REPETITION, UNKNOWN;

    companion object {
        fun parse(value: String?): Termination {
            val v = value.orEmpty().lowercase()
            return when {
                "time" in v || "timeout" in v -> TIMEOUT
                "resign" in v -> RESIGNATION
                "abandon" in v || "left" in v -> ABANDONMENT
                "agreement" in v || "mutual" in v -> AGREEMENT
                "stalemate" in v -> STALEMATE
                "insufficient" in v -> INSUFFICIENT_MATERIAL
                "fifty" in v || "50" in v -> FIFTY_MOVE
                "repetition" in v || "repet" in v -> REPETITION
                "mate" in v -> CHECKMATE
                else -> UNKNOWN
            }
        }
    }
}

data class RatingPoint(
    val date: Long,
    val rating: Int,
    val timeClass: TimeClass,
    val platform: Platform,
)

data class PlayerProfile(
    val id: String,
    val username: String,
    val platform: Platform,
    val displayName: String? = null,
    val country: String? = null,
    val avatarUrl: String? = null,
    val joinedAt: Long? = null,
    val ratings: Map<TimeClass, Int> = emptyMap(),
    val ratingHistory: List<RatingPoint> = emptyList(),
    val isOwnAccount: Boolean = false,
) {
    val displayLabel: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

data class OpeningInfo(
    val eco: String? = null,
    val name: String? = null,
    val moves: String? = null,
    val ply: Int = 0,
)

data class ClockInfo(
    val initialSeconds: Int? = null,
    val incrementSeconds: Int? = null,
    val whiteClocks: List<Int> = emptyList(),
    val blackClocks: List<Int> = emptyList(),
)

data class MoveRecord(
    val ply: Int,
    val san: String,
    val uci: String? = null,
    val fen: String,
    val clockSeconds: Int? = null,
    val evalCp: Int? = null,
    val mate: Int? = null,
    val isCapture: Boolean = false,
    val isCheck: Boolean = false,
    val isSacrifice: Boolean = false,
    val side: Side = if (ply % 2 == 1) Side.WHITE else Side.BLACK,
)

data class GameRecord(
    val id: String,
    val platform: Platform,
    val white: String,
    val black: String,
    val whiteElo: Int?,
    val blackElo: Int?,
    val userSide: Side,
    val result: GameResult,
    val termination: Termination,
    val playedAt: Long,
    val timeClass: TimeClass,
    val timeControl: String? = null,
    val rated: Boolean = true,
    val opening: OpeningInfo? = null,
    val clocks: ClockInfo = ClockInfo(),
    val moves: List<MoveRecord> = emptyList(),
    val pgn: String? = null,
    val whiteAccuracy: Float? = null,
    val blackAccuracy: Float? = null,
) {
    val userElo: Int? get() = if (userSide == Side.WHITE) whiteElo else blackElo
    val opponent: String get() = if (userSide == Side.WHITE) black else white
    val opponentElo: Int? get() = if (userSide == Side.WHITE) blackElo else whiteElo
    val moveCount: Int get() = moves.size
}

/** Win probability sample used to draw the evaluation graph. */
data class EvalPoint(val ply: Int, val winPercent: Float, val cp: Int? = null, val mate: Int? = null)

data class EngineLine(
    val moves: List<String> = emptyList(),
    val cp: Int? = null,
    val mate: Int? = null,
    val depth: Int = 0,
) {
    val isMate: Boolean get() = mate != null
    val scoreLabel: String get() = mate?.let { mateScore ->
        "M${kotlin.math.abs(mateScore)}"
    } ?: cp?.let { if (it >= 0) "+${it / 100f}" else "${it / 100f}" } ?: "0.00"
}

enum class MoveBadge(val symbol: String, val colorHex: String) {
    BOOK("", "#9AA0A6"),
    FORCED("", "#9AA0A6"),
    BEST("", "#81B64C"),
    GREAT("!", "#68A7F8"),
    BRILLIANT("!!", "#1BD0D8"),
    GOOD("", "#81B64C"),
    INACCURACY("?!", "#F3C969"),
    MISTAKE("?", "#F29B4C"),
    BLUNDER("??", "#E76A5E"),
}

data class MoveReview(
    val ply: Int,
    val san: String,
    val badge: MoveBadge,
    val winBefore: Float,
    val winAfter: Float,
    val winLoss: Float,
    val bestMoveUci: String? = null,
    val bestLine: List<String> = emptyList(),
    val evaluationCp: Int? = null,
    val mate: Int? = null,
    val comment: String? = null,
) {
    val isCritical: Boolean get() = badge in setOf(
        MoveBadge.INACCURACY, MoveBadge.MISTAKE, MoveBadge.BLUNDER, MoveBadge.BRILLIANT
    )
}

data class GameReviewReport(
    val gameId: String,
    val whiteAccuracy: Float,
    val blackAccuracy: Float,
    val whitePerformanceElo: Int,
    val blackPerformanceElo: Int,
    val reviews: List<MoveReview>,
    val evalGraph: List<EvalPoint>,
) {
    val blunders: Int get() = reviews.count { it.badge == MoveBadge.BLUNDER }
    val mistakes: Int get() = reviews.count { it.badge == MoveBadge.MISTAKE }
    val inaccuracies: Int get() = reviews.count { it.badge == MoveBadge.INACCURACY }
    val brilliancies: Int get() = reviews.count { it.badge == MoveBadge.BRILLIANT }
}
