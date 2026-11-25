package com.fredsystems.brilliantboard.domain.scouting

import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.domain.model.GameRecord
import com.fredsystems.brilliantboard.domain.model.GameResult
import com.fredsystems.brilliantboard.domain.model.MoveRecord
import com.fredsystems.brilliantboard.domain.model.Side
import com.fredsystems.brilliantboard.domain.model.Termination

data class Wdl(val wins: Int = 0, val draws: Int = 0, val losses: Int = 0) {
    val total: Int get() = wins + draws + losses
    val scorePercent: Float get() = if (total == 0) 0f else (wins + 0.5f * draws) * 100f / total
    val winRate: Float get() = if (total == 0) 0f else wins * 100f / total
    val drawRate: Float get() = if (total == 0) 0f else draws * 100f / total
    val lossRate: Float get() = if (total == 0) 0f else losses * 100f / total

    operator fun plus(other: Wdl): Wdl = Wdl(wins + other.wins, draws + other.draws, losses + other.losses)
}

fun Collection<GameResult>.toWdl(): Wdl = fold(Wdl()) { acc, result ->
    when (result) {
        GameResult.WIN -> acc.copy(wins = acc.wins + 1)
        GameResult.DRAW -> acc.copy(draws = acc.draws + 1)
        GameResult.LOSS -> acc.copy(losses = acc.losses + 1)
    }
}

enum class EndgameType { ROOK, QUEEN, MINOR, PAWN, NONE }

data class GameFeatures(
    val game: GameRecord,
    val side: Side,
    val result: GameResult,
    val eco: String?,
    val openingName: String?,
    val firstMoveUci: String?,
    val firstMoveSan: String?,
    val queensTraded: Boolean,
    val earlyPawnStorm: Boolean,
    val sacrifices: Int,
    val checks: Int,
    val timeTroubleBlunders: Int,
    val berserkBlunders: Int,
    val lostOnTime: Boolean,
    val resignedEarly: Boolean,
    val endgameType: EndgameType,
    val endgameAdvantage: Float,
    val moveNumberAtLoss: Int?,
) {
    val userMoves: List<MoveRecord> get() = game.moves.filter { it.side == side }
}

object GameFeatureExtractor {

    fun extract(game: GameRecord, side: Side, blunderPlies: Set<Int> = emptySet()): GameFeatures {
        val moves = game.moves
        val eco = game.opening?.eco
        val openingName = game.opening?.name
        val userMoves = moves.filter { it.side == side }
        val first = userMoves.firstOrNull()

        val clocks = if (side == Side.WHITE) game.clocks.whiteClocks else game.clocks.blackClocks
        val initial = game.clocks.initialSeconds ?: 180
        val increment = game.clocks.incrementSeconds ?: 0

        var timeTrouble = 0
        var berserk = 0
        userMoves.forEachIndexed { index, move ->
            if (move.ply !in blunderPlies) return@forEachIndexed
            val remaining = clocks.getOrNull(index) ?: return@forEachIndexed
            val previous = clocks.getOrNull(index - 1) ?: initial
            val clockShare = remaining.toFloat() / initial.coerceAtLeast(1)
            when {
                remaining < 30 -> timeTrouble++
                clockShare > 0.5f && previous - remaining + increment < 10 -> berserk++
            }
        }

        val endgameFen = moves.lastOrNull()?.fen ?: Position.START.fen()
        val endgame = runCatching { Position.fromFen(endgameFen) }.getOrNull()
        val (endgameType, advantage) = endgame?.let { analyzeEndgame(it, side) }
            ?: EndgameType.NONE to 0f

        return GameFeatures(
            game = game,
            side = side,
            result = game.result,
            eco = eco,
            openingName = openingName,
            firstMoveUci = first?.uci,
            firstMoveSan = first?.san,
            queensTraded = runCatching {
                Position.fromFen(moves.lastOrNull()?.fen ?: Position.START.fen())
                    .squares.count { it.lowercaseChar() == 'q' } == 0
            }.getOrDefault(false),
            earlyPawnStorm = userMoves.take(12).count {
                it.san.firstOrNull()?.lowercaseChar() in listOf('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h')
            } >= 6,
            sacrifices = userMoves.count { it.isSacrifice },
            checks = userMoves.count { it.isCheck },
            timeTroubleBlunders = timeTrouble,
            berserkBlunders = berserk,
            lostOnTime = game.termination == Termination.TIMEOUT && game.result == GameResult.LOSS,
            resignedEarly = game.termination == Termination.RESIGNATION && game.result == GameResult.LOSS &&
                (game.moves.size / 2) <= 30,
            endgameType = endgameType,
            endgameAdvantage = advantage,
            moveNumberAtLoss = if (game.result == GameResult.LOSS) moves.size / 2 + 1 else null,
        )
    }

    private fun analyzeEndgame(position: Position, side: Side): Pair<EndgameType, Float> {
        val whiteMaterial = material(position, uppercase = true)
        val blackMaterial = material(position, uppercase = false)
        val raw = (whiteMaterial - blackMaterial) / 100f
        val balance = if (side == Side.WHITE) raw else -raw
        val minority = minOf(whiteMaterial, blackMaterial)
        val type = when {
            position.squares.count { it == 'Q' || it == 'q' } > 0 -> EndgameType.QUEEN
            position.squares.count { it.lowercaseChar() == 'r' } > 0 -> EndgameType.ROOK
            position.squares.count { it.lowercaseChar() == 'b' || it.lowercaseChar() == 'n' } > 0 -> EndgameType.MINOR
            position.squares.count { it.lowercaseChar() == 'p' } > 0 -> EndgameType.PAWN
            else -> EndgameType.NONE
        }
        return type to (if (minority > 0) balance.coerceAtLeast(0f) else 0f)
    }

    private fun material(position: Position, uppercase: Boolean): Int {
        val values = mapOf('p' to 100, 'n' to 320, 'b' to 330, 'r' to 500, 'q' to 900)
        return position.squares.sumOf { piece ->
            if (piece == '.') 0 else if (piece.isUpperCase() == uppercase) values[piece.lowercaseChar()] ?: 0 else 0
        }
    }
}

data class OpeningStat(
    val eco: String,
    val openingName: String,
    val side: Side,
    val wdl: Wdl,
    val games: Int,
    val collapsePly: Int?,
    val avgOpponentElo: Int?,
)

object OpeningLeakFinder {

    fun analyze(
        games: List<GameFeatures>,
        side: Side,
        minGames: Int = 2,
    ): List<OpeningStat> {
        return games.filter { it.side == side && !it.eco.isNullOrBlank() }
            .groupBy { it.eco!! }
            .map { (eco, group) ->
                val wdl = group.fold(Wdl()) { acc, feature -> acc + Wdl(
                    if (feature.result == GameResult.WIN) 1 else 0,
                    if (feature.result == GameResult.DRAW) 1 else 0,
                    if (feature.result == GameResult.LOSS) 1 else 0,
                ) }
                val lossesByPly = group.mapNotNull { it.moveNumberAtLoss }
                    .groupingBy { it }.eachCount()
                val elos = group.mapNotNull { it.game.opponentElo }
                OpeningStat(
                    eco = eco,
                    openingName = group.firstNotNullOfOrNull { it.openingName }.orEmpty(),
                    side = side,
                    wdl = wdl,
                    games = group.size,
                    collapsePly = lossesByPly.maxByOrNull { it.value }?.key,
                    avgOpponentElo = elos.takeIf { it.isNotEmpty() }?.sum()?.div(elos.size),
                )
            }
            .filter { it.games >= minGames }
            .sortedBy { it.wdl.scorePercent }
    }

    fun headline(stat: OpeningStat): String {
        val name = stat.openingName.ifBlank { "ECO ${stat.eco}" }
        val ply = stat.collapsePly?.let { " — results collapse around move $it" }.orEmpty()
        return "$name: ${stat.wdl.scorePercent.toInt()}% over ${stat.games} games$ply"
    }
}

data class ClockStats(
    val avgSecondsPerMove: Float,
    val timeTroubleBlunderRate: Float,
    val berserkRate: Float,
    val timeoutLossRate: Float,
    val totalMoves: Int,
)

object ClockIQ {
    fun analyze(features: List<GameFeatures>, side: Side): ClockStats {
        val relevant = features.filter { it.side == side }
        val totalMoves = relevant.sumOf { it.userMoves.size }
        val totalClock = relevant.fold(0f) { acc, game ->
            val clocks = if (side == Side.WHITE) game.game.clocks.whiteClocks else game.game.clocks.blackClocks
            acc + clocks.fold(0f) { sum, seconds -> sum + seconds.toFloat() }
        }
        val avg = if (totalMoves == 0) 0f else totalClock / totalMoves
        val blunders = relevant.sumOf { it.timeTroubleBlunders + it.berserkBlunders }
        val timeTrouble = relevant.sumOf { it.timeTroubleBlunders }
        val berserk = relevant.sumOf { it.berserkBlunders }
        val losses = relevant.count { it.result == GameResult.LOSS }
        val timeouts = relevant.count { it.lostOnTime }
        return ClockStats(
            avgSecondsPerMove = avg,
            timeTroubleBlunderRate = if (blunders == 0) 0f else timeTrouble * 100f / blunders,
            berserkRate = if (blunders == 0) 0f else berserk * 100f / blunders,
            timeoutLossRate = if (losses == 0) 0f else timeouts * 100f / losses,
            totalMoves = totalMoves,
        )
    }
}

enum class GamePhase(val label: String) {
    OPENING("Opening"),
    MIDDLEGAME("Middlegame"),
    ENDGAME("Endgame"),
}

data class PhaseStat(val phase: GamePhase, val wdl: Wdl, val games: Int, val avgAccuracy: Float?)

object PhaseMastery {
    fun phaseFor(moveNumber: Int, nonPawnPieces: Int): GamePhase = when {
        moveNumber <= 12 -> GamePhase.OPENING
        nonPawnPieces <= 6 || moveNumber >= 45 -> GamePhase.ENDGAME
        else -> GamePhase.MIDDLEGAME
    }

    fun analyze(
        games: List<GameRecord>,
        side: Side,
        accuracyByGame: Map<String, Float> = emptyMap(),
    ): List<PhaseStat> {
        return GamePhase.entries.map { phase ->
            val relevant = games.filter { game ->
                val moves = game.moves.filter { it.side == side }
                val last = moves.lastOrNull() ?: return@filter false
                val fen = last.fen
                val nonPawn = runCatching {
                    Position.fromFen(fen).squares.count { it != '.' && it.lowercaseChar() != 'p' }
                }.getOrDefault(32)
                phaseFor(last.ply / 2 + 1, nonPawn) == phase
            }
            val accuracies = relevant.mapNotNull { accuracyByGame[it.id] }
            PhaseStat(
                phase = phase,
                wdl = relevant.map { it.result }.toWdl(),
                games = relevant.size,
                avgAccuracy = accuracies.takeIf { it.isNotEmpty() }?.sum()?.div(accuracies.size),
            )
        }
    }
}

data class EndgameStat(
    val type: EndgameType,
    val wdl: Wdl,
    val games: Int,
    val conversionRate: Float,
)

object EndgameConversionLab {
    fun analyze(features: List<GameFeatures>, minAdvantage: Float = 2.0f): List<EndgameStat> {
        return EndgameType.entries.filter { it != EndgameType.NONE }.map { type ->
            val qualifying = features.filter {
                it.endgameType == type && it.endgameAdvantage >= minAdvantage
            }
            EndgameStat(
                type = type,
                wdl = qualifying.map { it.result }.toWdl(),
                games = qualifying.size,
                conversionRate = if (qualifying.isEmpty()) 0f else qualifying.count {
                    it.result == GameResult.WIN
                } * 100f / qualifying.size,
            )
        }
    }
}
