package com.recomp.gameshub.data.local

import com.recomp.gameshub.data.remote.GameMetadataDto
import com.recomp.gameshub.data.remote.IndexEntryDto
import com.recomp.gameshub.domain.model.DownloadPhase
import com.recomp.gameshub.domain.model.DownloadTask
import com.recomp.gameshub.domain.model.GameDetail
import com.recomp.gameshub.domain.model.GameStatus
import com.recomp.gameshub.domain.model.GameSummary
import com.recomp.gameshub.domain.model.InstalledGame
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json

fun IndexEntryDto.toEntity(fetchedAt: Long): GameEntity =
    GameEntity(
        slug = slug,
        name = name,
        status = status?.let { GameStatus.from(it).name } ?: GameStatus.UNKNOWN.name,
        version = version,
        description = "",
        originalPlatform = platform,
        author = null,
        sourceRepo = null,
        downloadUrl = null,
        fileSizeBytes = 0L,
        tagsJson = "[]",
        banner = null,
        cover = cover,
        screenshotsJson = "[]",
        lastUpdated = lastUpdated,
        fetchedAt = fetchedAt,
    )

fun GameMetadataDto.toEntity(fetchedAt: Long): GameEntity =
    GameEntity(
        slug = slug,
        name = name,
        status = GameStatus.from(status).name,
        version = version,
        description = description,
        originalPlatform = originalPlatform,
        author = author,
        sourceRepo = sourceRepo,
        downloadUrl = downloadUrl,
        fileSizeBytes = fileSizeBytes,
        tagsJson = json.encodeToString(tags),
        banner = banner,
        cover = cover,
        screenshotsJson = json.encodeToString(screenshots),
        lastUpdated = lastUpdated,
        fetchedAt = fetchedAt,
    )

fun GameEntity.toSummary(): GameSummary =
    GameSummary(
        slug = slug,
        name = name,
        status = GameStatus.from(status),
        version = version,
        originalPlatform = originalPlatform,
        cover = cover,
        lastUpdated = lastUpdated,
    )

fun GameEntity.toDetail(): GameDetail =
    GameDetail(
        summary = toSummary(),
        description = description,
        author = author,
        sourceRepo = sourceRepo,
        downloadUrl = downloadUrl,
        fileSizeBytes = fileSizeBytes,
        tags = json.decodeFromString<List<String>>(tagsJson),
        banner = banner,
        cover = cover,
        screenshots = json.decodeFromString<List<String>>(screenshotsJson),
        sha256 = null,
        lastUpdated = lastUpdated,
    )

fun DownloadTask.toEntity(): DownloadEntity =
    DownloadEntity(
        id = id,
        gameName = gameName,
        url = url,
        localPath = localPath,
        fileName = fileName,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        phase = phase.name,
        errorMessage = errorMessage,
        addedAt = addedAt,
        completedAt = completedAt,
    )

fun DownloadEntity.toTask(): DownloadTask =
    DownloadTask(
        id = id,
        gameName = gameName,
        url = url,
        localPath = localPath,
        fileName = fileName,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        speedBytesPerSec = 0L,
        phase = DownloadPhase.valueOf(phase),
        errorMessage = errorMessage,
        addedAt = addedAt,
        completedAt = completedAt,
    )

fun InstalledGameEntity.toDomain(): InstalledGame =
    InstalledGame(
        slug = slug,
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        updatedAt = updatedAt,
    )