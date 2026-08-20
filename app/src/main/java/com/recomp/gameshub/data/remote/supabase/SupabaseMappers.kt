package com.recomp.gameshub.data.remote.supabase

import com.recomp.gameshub.data.local.GameEntity
import com.recomp.gameshub.domain.model.GameStatus
import com.recomp.gameshub.domain.model.GameSubmission
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true }

fun GameRow.toEntity(fetchedAt: Long): GameEntity =
    GameEntity(
        slug = slug,
        name = title,
        status = status.let { GameStatus.from(it).name },
        version = version,
        description = description,
        originalPlatform = originalPlatform,
        author = author,
        sourceRepo = sourceRepoUrl,
        downloadUrl = apkUrl,
        fileSizeBytes = fileSizeBytes,
        tagsJson = json.encodeToString(tags),
        banner = bannerUrl,
        cover = coverUrl,
        screenshotsJson = json.encodeToString(screenshots?.map { it.imageUrl }.orEmpty()),
        lastUpdated = updatedAt,
        fetchedAt = fetchedAt,
    )

fun GameRow.toSubmission(): GameSubmission =
    GameSubmission(
        submissionId = id ?: UUID.randomUUID().toString(),
        slug = slug,
        name = title,
        status = reviewStatus.ifBlank { REVIEW_PENDING },
        devStatus = status,
        version = version,
        description = description,
        originalPlatform = originalPlatform,
        author = author,
        sourceRepo = sourceRepoUrl,
        apkUrl = apkUrl,
        fileSizeBytes = fileSizeBytes,
        tags = tags,
        coverUrl = coverUrl,
        bannerUrl = bannerUrl,
        screenshots = screenshots.orEmpty()
            .sortedBy { it.sortOrder }
            .map { it.imageUrl }
            .filter { it.isNotBlank() },
        reviewReason = reviewReason,
        submittedAt = createdAt,
    )

/** Validates and normalizes a slug to [a-z0-9-]. Returns null if empty. */
fun String.toSlugOrNull(): String? {
    val normalized = lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return normalized.ifBlank { null }
}
