package com.fredsystems.brilliantboard.domain.scouting

import com.fredsystems.brilliantboard.domain.model.GameRecord
import com.fredsystems.brilliantboard.domain.model.GameResult
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.domain.model.Side

data class TiltProfile(
    val afterOneLoss: Float,
    val afterTwoLosses: Float,
    val afterThreePlusLosses: Float,
    val baselineWinRate: Float,
    val earlyResignationRate: Float,
    val playsOnDownPieceRate: Float,
    val hourlyHeatmap: List<Float>,
    val weekdayHeatmap: List<Float>,
)

object TiltAnalytics {
    fun analyze(features: List<GameFeatures>, side: Side): TiltProfile {
        val ordered = features.filter { it.side == side }.sortedBy { it.game.playedAt }
        val baseline = ordered.map { it.result }.toWdl().scorePercent

        val groups = mutableMapOf<Int, MutableList<GameResult>>()
        var streak = 0
        ordered.forEach { feature ->
            val bucket = streak.coerceAtMost(3)
            groups.getOrPut(bucket) { mutableListOf() } += feature.result
            streak = if (feature.result == GameResult.LOSS) streak + 1 else 0
        }

        fun rate(bucket: Int): Float {
            val results = groups[bucket].orEmpty()
            return if (results.isEmpty()) baseline else results.toWdl().scorePercent
        }

        val hourBuckets = MutableList(24) { mutableListOf<GameResult>() }
        val dayBuckets = MutableList(7) { mutableListOf<GameResult>() }
        ordered.forEach { feature ->
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = feature.game.playedAt }
            hourBuckets[calendar.get(java.util.Calendar.HOUR_OF_DAY)] += feature.result
            dayBuckets[(calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7] += feature.result
        }

        val losses = ordered.filter { it.result == GameResult.LOSS }
        return TiltProfile(
            afterOneLoss = rate(1),
            afterTwoLosses = rate(2),
            afterThreePlusLosses = rate(3),
            baselineWinRate = baseline,
            earlyResignationRate = if (losses.isEmpty()) 0f else losses.count { it.resignedEarly } * 100f / losses.size,
            playsOnDownPieceRate = if (losses.isEmpty()) 0f else losses.count { !it.resignedEarly } * 100f / losses.size,
            hourlyHeatmap = hourBuckets.map { if (it.isEmpty()) Float.NaN else it.toWdl().scorePercent },
            weekdayHeatmap = dayBuckets.map { if (it.isEmpty()) Float.NaN else it.toWdl().scorePercent },
        )
    }
}

data class StyleProfile(
    val aggressionScore: Float,
    val tradeHappiness: Float,
    val flaggingVulnerability: Float,
    val tiltSusceptibility: Float,
    val averageGameLength: Float,
    val mostPlayedFirstMove: String?,
    val predictability: Float,
)

object StyleProfiler {
    fun analyze(
        features: List<GameFeatures>,
        side: Side,
        tilt: TiltProfile,
    ): StyleProfile {
        val relevant = features.filter { it.side == side }
        val totalMoves = relevant.sumOf { it.userMoves.size }.coerceAtLeast(1)
        val aggression = relevant.sumOf { it.sacrifices * 3 + it.checks + it.earlyPawnStorm.compareTo(false) * 2 }
        val aggressionScore = (aggression * 100f / (totalMoves * 5f)).coerceIn(0f, 100f)
        val tradeHappiness = if (relevant.isEmpty()) 0f else relevant.count { it.queensTraded } * 100f / relevant.size
        val wins = relevant.filter { it.result == GameResult.WIN }
        val flagging = if (wins.isEmpty()) 0f else wins.count {
            it.game.termination == com.fredsystems.brilliantboard.domain.model.Termination.TIMEOUT
        } * 100f / wins.size
        val topMove = relevant.mapNotNull { it.firstMoveSan }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }
        val predictability = if (relevant.isEmpty() || topMove == null) 0f else
            topMove.value * 100f / relevant.size

        return StyleProfile(
            aggressionScore = aggressionScore,
            tradeHappiness = tradeHappiness,
            flaggingVulnerability = flagging,
            tiltSusceptibility = tilt.earlyResignationRate,
            averageGameLength = if (relevant.isEmpty()) 0f else
                relevant.sumOf { it.game.moveCount }.toFloat() / relevant.size,
            mostPlayedFirstMove = topMove?.key,
            predictability = predictability,
        )
    }
}

data class MoveFrequency(val move: String, val count: Int, val percentage: Float)

object RepertoireBreadth {
    fun topFirstMoves(features: List<GameFeatures>, side: Side, limit: Int = 3): List<MoveFrequency> {
        val relevant = features.filter { it.side == side }
        val counts = relevant.mapNotNull { it.firstMoveSan }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
        val total = counts.sumOf { it.second }.coerceAtLeast(1)
        return counts.take(limit).map { (move, count) ->
            MoveFrequency(move, count, count * 100f / total)
        }
    }
}

data class HeadToHead(
    val games: Int,
    val wdl: Wdl,
    val platforms: Set<Platform>,
    val lastPlayedAt: Long?,
)

object HeadToHeadFinder {
    fun find(games: List<GameRecord>, opponent: String, ownAccounts: Set<String>): HeadToHead {
        val matches = games.filter { game ->
            (game.white.equals(opponent, true) && game.black.lowercase() in ownAccounts) ||
                (game.black.equals(opponent, true) && game.white.lowercase() in ownAccounts)
        }
        return HeadToHead(
            games = matches.size,
            wdl = matches.map { it.result }.toWdl(),
            platforms = matches.map { it.platform }.toSet(),
            lastPlayedAt = matches.maxOfOrNull { it.playedAt },
        )
    }
}

data class GameplanCard(
    val opponentName: String,
    val platform: Platform,
    val gamesAnalyzed: Int,
    val worstAsWhite: List<OpeningStat>,
    val worstAsBlack: List<OpeningStat>,
    val topFirstMoves: List<MoveFrequency>,
    val style: StyleProfile,
    val tilt: TiltProfile,
    val headToHead: HeadToHead,
    val recommendations: List<String>,
)

object OpponentGameplan {
    fun build(
        opponentName: String,
        platform: Platform,
        games: List<GameRecord>,
        ownAccounts: Set<String>,
        ownRepertoire: Set<String> = emptySet(),
    ): GameplanCard {
        val whiteFeatures = games.map { GameFeatureExtractor.extract(it, Side.WHITE) }
        val blackFeatures = games.map { GameFeatureExtractor.extract(it, Side.BLACK) }
        val allFeatures = whiteFeatures + blackFeatures
        val worstWhite = OpeningLeakFinder.analyze(whiteFeatures, Side.WHITE, minGames = 1).take(3)
        val worstBlack = OpeningLeakFinder.analyze(blackFeatures, Side.BLACK, minGames = 1).take(3)
        val topMoves = RepertoireBreadth.topFirstMoves(allFeatures, Side.WHITE, 3)
        val tilt = TiltAnalytics.analyze(allFeatures, Side.WHITE)
        val style = StyleProfiler.analyze(allFeatures, Side.WHITE, tilt)
        val headToHead = HeadToHeadFinder.find(games, opponentName, ownAccounts)

        val recommendations = mutableListOf<String>()
        worstBlack.firstOrNull()?.let {
            recommendations.add("They score only ${it.wdl.scorePercent.toInt()}% with Black in ${it.openingName.ifBlank { it.eco }} — prepare this line.")
        }
        worstWhite.firstOrNull()?.let {
            recommendations.add("As White their weakest opening is ${it.openingName.ifBlank { it.eco }} (${it.wdl.scorePercent.toInt()}%).")
        }
        if (style.flaggingVulnerability > 40f) {
            recommendations.add("They win many games on time — keep material even and play for the clock.")
        }
        if (tilt.afterTwoLosses < tilt.baselineWinRate - 10f) {
            recommendations.add("Tilt susceptibility detected: their score drops after two consecutive losses.")
        }
        if (topMoves.size >= 2 && topMoves[0].percentage > 60f) {
            recommendations.add("Predictable repertoire: ${topMoves[0].move} in ${topMoves[0].percentage.toInt()}% of games.")
        }
        ownRepertoire.firstOrNull()?.let {
            recommendations.add("Your $it repertoire matches a weak spot — prioritise it.")
        }

        return GameplanCard(
            opponentName = opponentName,
            platform = platform,
            gamesAnalyzed = games.size,
            worstAsWhite = worstWhite,
            worstAsBlack = worstBlack,
            topFirstMoves = topMoves,
            style = style,
            tilt = tilt,
            headToHead = headToHead,
            recommendations = recommendations,
        )
    }
}
