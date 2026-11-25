package com.fredsystems.brilliantboard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isOwn DESC, username ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isOwn = 1")
    suspend fun ownAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE username = :username AND platform = :platform LIMIT 1")
    suspend fun find(username: String, platform: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE username LIKE :pattern ORDER BY isOwn DESC LIMIT 20")
    suspend fun search(pattern: String): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE ownerUsername = :username ORDER BY playedAt DESC LIMIT :limit")
    fun observeGames(username: String, limit: Int = 10_000): Flow<List<GameEntity>>

    @Query(
        """SELECT * FROM games WHERE ownerUsername = :username AND (:eco IS NULL OR eco = :eco)
        AND (:side IS NULL OR userSide = :side) AND (:timeClass IS NULL OR timeClass = :timeClass)
        AND playedAt BETWEEN :from AND :to ORDER BY playedAt DESC"""
    )
    suspend fun filter(
        username: String,
        eco: String?,
        side: String?,
        timeClass: String?,
        from: Long,
        to: Long,
    ): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    suspend fun find(id: String): GameEntity?

    @Query("SELECT COUNT(*) FROM games WHERE ownerUsername = :username")
    suspend fun count(username: String): Int

    @Query("SELECT DISTINCT white AS name FROM games WHERE white LIKE :pattern ORDER BY name LIMIT 15")
    suspend fun whiteNames(pattern: String): List<String>

    @Query("SELECT DISTINCT black AS name FROM games WHERE black LIKE :pattern ORDER BY name LIMIT 15")
    suspend fun blackNames(pattern: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(games: List<GameEntity>)

    @Query("DELETE FROM games WHERE ownerUsername = :username")
    suspend fun deleteFor(username: String)
}

@Dao
interface RatingDao {
    @Query("SELECT * FROM rating_points WHERE username = :username AND platform = :platform ORDER BY date ASC")
    suspend fun forPlayer(username: String, platform: String): List<RatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(points: List<RatingEntity>)

    @Query("DELETE FROM rating_points WHERE username = :username AND platform = :platform")
    suspend fun deleteFor(username: String, platform: String)
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE key = :key LIMIT 1")
    suspend fun find(key: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)
}

@androidx.room.Database(
    entities = [AccountEntity::class, GameEntity::class, RatingEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : androidx.room.RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun gameDao(): GameDao
    abstract fun ratingDao(): RatingDao
    abstract fun syncStateDao(): SyncStateDao
}
