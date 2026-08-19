package com.recomp.gameshub.domain.model

enum class DownloadPhase {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean
        get() = this == PENDING || this == DOWNLOADING || this == PAUSED
}

data class DownloadTask(
    val id: String,
    val gameName: String,
    val url: String,
    val localPath: String,
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long = 0L,
    val phase: DownloadPhase,
    val errorMessage: String? = null,
    val addedAt: Long,
    val completedAt: Long? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isActive: Boolean get() = phase.isActive

    val remainingBytes: Long get() = (totalBytes - downloadedBytes).coerceAtLeast(0L)

    fun withDelta(speed: Long, downloaded: Long, total: Long): DownloadTask = copy(
        speedBytesPerSec = speed,
        downloadedBytes = downloaded,
        totalBytes = total,
    )
}