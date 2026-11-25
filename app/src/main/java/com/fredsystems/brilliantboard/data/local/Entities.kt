package com.fredsystems.brilliantboard.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts", indices = [Index(value = ["username", "platform"], unique = true)])
data class AccountEntity(
    @PrimaryKey val id: String,
    val username: String,
    val platform: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val country: String? = null,
    val isOwn: Boolean = false,
    val lastSyncedAt: Long = 0L,
)

@Entity(
    tableName = "games",
    indices = [
        Index(value = ["ownerUsername"]),
        Index(value = ["playedAt"]),
        Index(value = ["eco"]),
        Index(value = ["platform", "ownerUsername", "playedAt"]),
    ],
)
data class GameEntity(
    @PrimaryKey val id: String,
    val ownerUsername: String,
    val platform: String,
    val white: String,
    val black: String,
    val whiteElo: Int?,
    val blackElo: Int?,
    val userSide: String,
    val result: String,
    val termination: String,
    val playedAt: Long,
    val timeClass: String,
    val timeControl: String?,
    val eco: String?,
    val openingName: String?,
    val pgn: String?,
    val whiteAccuracy: Float? = null,
    val blackAccuracy: Float? = null,
)

@Entity(tableName = "rating_points", indices = [Index(value = ["username", "platform", "timeClass"])])
data class RatingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val platform: String,
    val timeClass: String,
    val rating: Int,
    val date: Long,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val lastSyncedAt: Long = 0L,
    val cursor: String? = null,
    val etag: String? = null,
)
