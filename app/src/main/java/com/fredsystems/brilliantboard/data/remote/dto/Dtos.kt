package com.fredsystems.brilliantboard.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChessComProfile(
    val username: String = "",
    val name: String? = null,
    val country: String? = null,
    val avatar: String? = null,
    val joined: Long? = null,
    @SerialName("last_online") val lastOnline: Long? = null,
)

@Serializable
data class ChessComRating(
    val rating: Int = 0,
    val date: Long = 0,
)

@Serializable
data class ChessComRatingBucket(
    val last: ChessComRating? = null,
    val best: ChessComRating? = null,
)

@Serializable
data class ChessComStats(
    @SerialName("chess_bullet") val chessBullet: ChessComRatingBucket? = null,
    @SerialName("chess_blitz") val chessBlitz: ChessComRatingBucket? = null,
    @SerialName("chess_rapid") val chessRapid: ChessComRatingBucket? = null,
    @SerialName("chess_daily") val chessDaily: ChessComRatingBucket? = null,
)

@Serializable
data class ChessComArchives(val archives: List<String> = emptyList())

@Serializable
data class ChessComPlayer(
    val username: String? = null,
    val rating: Int? = null,
    val result: String? = null,
)

@Serializable
data class ChessComGame(
    val url: String? = null,
    val pgn: String? = null,
    @SerialName("time_control") val timeControl: String? = null,
    @SerialName("end_time") val endTime: Long = 0,
    val rated: Boolean = true,
    val fen: String? = null,
    @SerialName("time_class") val timeClass: String? = null,
    val white: ChessComPlayer? = null,
    val black: ChessComPlayer? = null,
)

@Serializable
data class ChessComGamePage(val games: List<ChessComGame> = emptyList())

@Serializable
data class LichessPerf(
    val games: Int = 0,
    val rating: Int = 0,
    val prog: Int = 0,
)

@Serializable
data class LichessUser(
    val id: String? = null,
    val username: String = "",
    val name: String? = null,
    val country: String? = null,
    @SerialName("createdAt") val createdAt: Long? = null,
    val perfs: Map<String, LichessPerf>? = null,
)

@Serializable
data class LichessGameUser(
    val id: String? = null,
    val name: String? = null,
    val rating: Int? = null,
)

@Serializable
data class LichessGame(
    val id: String? = null,
    val pgn: String? = null,
    val users: Map<String, LichessGameUser>? = null,
    val clocks: Map<String, List<Int>>? = null,
)

@Serializable
data class ExplorerOpening(
    val eco: String? = null,
    val name: String? = null,
)

@Serializable
data class ExplorerMove(
    val san: String = "",
    val uci: String = "",
    val white: Int = 0,
    val draw: Int = 0,
    val black: Int = 0,
    @SerialName("averageRating") val averageRating: Int? = null,
)

@Serializable
data class ExplorerResponse(
    val opening: ExplorerOpening? = null,
    val moves: List<ExplorerMove> = emptyList(),
)
