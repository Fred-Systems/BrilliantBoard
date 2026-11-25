package com.fredsystems.brilliantboard.data.repository

import com.fredsystems.brilliantboard.data.local.AccountEntity
import com.fredsystems.brilliantboard.data.local.GameEntity
import com.fredsystems.brilliantboard.data.local.RatingEntity
import com.fredsystems.brilliantboard.data.remote.dto.ChessComProfile
import com.fredsystems.brilliantboard.data.remote.dto.ChessComStats
import com.fredsystems.brilliantboard.data.remote.dto.LichessUser
import com.fredsystems.brilliantboard.domain.model.GameRecord
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.domain.model.PlayerProfile
import com.fredsystems.brilliantboard.domain.model.RatingPoint
import com.fredsystems.brilliantboard.domain.model.TimeClass

fun GameRecord.toEntity(owner: String): GameEntity = GameEntity(
    id = id,
    ownerUsername = owner.lowercase(),
    platform = platform.name,
    white = white,
    black = black,
    whiteElo = whiteElo,
    blackElo = blackElo,
    userSide = userSide.name,
    result = result.name,
    termination = termination.name,
    playedAt = playedAt,
    timeClass = timeClass.name,
    timeControl = timeControl,
    eco = opening?.eco,
    openingName = opening?.name,
    pgn = pgn,
    whiteAccuracy = whiteAccuracy,
    blackAccuracy = blackAccuracy,
)

fun GameEntity.toRecord(pgnParser: com.fredsystems.brilliantboard.data.scraper.PgnParser): GameRecord? {
    val parsed = pgn?.let {
        pgnParser.parse(it, Platform.valueOf(platform), user = ownerUsername, fallbackId = id)
    }
    if (parsed != null) {
        return parsed.copy(
            id = id,
            whiteAccuracy = whiteAccuracy ?: parsed.whiteAccuracy,
            blackAccuracy = blackAccuracy ?: parsed.blackAccuracy,
        )
    }
    return null
}

fun ChessComProfile.toProfile(platform: Platform, stats: ChessComStats? = null): PlayerProfile {
    val buckets = mapOf(
        TimeClass.BULLET to stats?.chessBullet?.last?.rating,
        TimeClass.BLITZ to stats?.chessBlitz?.last?.rating,
        TimeClass.RAPID to stats?.chessRapid?.last?.rating,
        TimeClass.DAILY to stats?.chessDaily?.last?.rating,
    ).mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
    return PlayerProfile(
        id = username,
        username = username,
        platform = platform,
        displayName = name,
        country = country,
        avatarUrl = avatar,
        joinedAt = joined,
        ratings = buckets,
    )
}

fun LichessUser.toProfile(ratingHistory: List<Triple<Long, String, Int>> = emptyList()): PlayerProfile {
    val ratings = perfs.orEmpty().mapNotNull { (key, value) ->
        TimeClass.from(key).takeIf { it != TimeClass.UNKNOWN }?.to(value.rating)
    }.toMap()
    return PlayerProfile(
        id = id ?: username,
        username = username,
        platform = Platform.LICHESS,
        displayName = name,
        country = country,
        avatarUrl = null,
        joinedAt = createdAt,
        ratings = ratings,
        ratingHistory = ratingHistory.map { (date, timeClass, rating) ->
            RatingPoint(date, rating, TimeClass.from(timeClass), Platform.LICHESS)
        },
    )
}

fun RatingPoint.toEntity(username: String, platform: Platform): RatingEntity = RatingEntity(
    username = username.lowercase(),
    platform = platform.name,
    timeClass = timeClass.name,
    rating = rating,
    date = date,
)

fun AccountEntity.toProfile(): PlayerProfile = PlayerProfile(
    id = id,
    username = username,
    platform = Platform.valueOf(platform),
    displayName = displayName,
    avatarUrl = avatarUrl,
    country = country,
    isOwnAccount = isOwn,
)
