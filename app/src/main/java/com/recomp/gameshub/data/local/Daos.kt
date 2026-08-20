package com.recomp.gameshub.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE slug = :slug")
    fun observeDetail(slug: String): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE slug = :slug")
    suspend fun get(slug: String): GameEntity?

    @Upsert
    suspend fun upsert(entity: GameEntity)

    @Upsert
    suspend fun upsertAll(entities: List<GameEntity>)

    @Query("DELETE FROM games WHERE slug NOT IN (:keep)")
    suspend fun deleteWhereMissing(keep: List<String>)

    @Query("DELETE FROM games")
    suspend fun clear()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: String): DownloadEntity?

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE downloads SET phase = :phase, errorMessage = NULL WHERE phase IN ('PENDING', 'DOWNLOADING')")
    suspend fun resetInterrupted(phase: String)
}

@Dao
interface InstalledGamesDao {
    @Query("SELECT * FROM installed_games WHERE slug = :slug")
    fun observeBySlug(slug: String): Flow<InstalledGameEntity?>

    @Upsert
    suspend fun upsert(entity: InstalledGameEntity)
}