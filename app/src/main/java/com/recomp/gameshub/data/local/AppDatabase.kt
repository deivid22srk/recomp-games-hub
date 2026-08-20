package com.recomp.gameshub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [GameEntity::class, DownloadEntity::class, InstalledGameEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun downloadDao(): DownloadDao
    abstract fun installedGamesDao(): InstalledGamesDao
}