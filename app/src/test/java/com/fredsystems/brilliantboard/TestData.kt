package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.domain.model.ClockInfo
import com.fredsystems.brilliantboard.domain.model.GameRecord
import com.fredsystems.brilliantboard.domain.model.GameResult
import com.fredsystems.brilliantboard.domain.model.MoveRecord
import com.fredsystems.brilliantboard.domain.model.OpeningInfo
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.domain.model.Side
import com.fredsystems.brilliantboard.domain.model.Termination
import com.fredsystems.brilliantboard.domain.model.TimeClass

internal fun testGame(
    id: String = "g1",
    white: String = "Alice",
    black: String = "Bob",
    userSide: Side = Side.WHITE,
    result: GameResult = GameResult.WIN,
    eco: String = "B01",
    opening: String = "Scandinavian Defense",
    playedAt: Long = 1_700_000_000_000,
    whiteElo: Int? = 1800,
    blackElo: Int? = 1780,
    termination: Termination = Termination.CHECKMATE,
    moveCount: Int = 10,
    timeClass: TimeClass = TimeClass.BLITZ,
    clocks: ClockInfo = ClockInfo(),
    moves: List<MoveRecord> = emptyList(),
) = GameRecord(
    id = id,
    platform = Platform.CHESS_COM,
    white = white,
    black = black,
    whiteElo = whiteElo,
    blackElo = blackElo,
    userSide = userSide,
    result = result,
    termination = termination,
    playedAt = playedAt,
    timeClass = timeClass,
    opening = OpeningInfo(eco, opening),
    clocks = clocks,
    moves = moves,
)
