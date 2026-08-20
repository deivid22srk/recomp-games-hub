package com.recomp.gameshub.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val slug: String,
    val name: String,
    val status: String,
    val version: String?,
    val description: String,
    val originalPlatform: String?,
    val author: String?,
    val sourceRepo: String?,
    val downloadUrl: String?,
    val fileSizeBytes: Long,
    val tagsJson: String,
    val banner: String?,
    val cover: String?,
    val screenshotsJson: String,
    val lastUpdated: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val gameName: String,
    val url: String,
    val localPath: String,
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val phase: String,
    val errorMessage: String?,
    val addedAt: Long,
    val completedAt: Long?,
)

@Entity(tableName = "installed_games")
data class InstalledGameEntity(
    @PrimaryKey val slug: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val updatedAt: Long,
)