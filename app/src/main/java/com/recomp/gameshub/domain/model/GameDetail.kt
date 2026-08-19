package com.recomp.gameshub.domain.model

data class GameDetail(
    val summary: GameSummary,
    val description: String,
    val author: String?,
    val sourceRepo: String?,
    val downloadUrl: String?,
    val fileSizeBytes: Long,
    val tags: List<String>,
    val banner: String?,
    val cover: String?,
    val screenshots: List<String>,
    val sha256: String?,
    val lastUpdated: String?,
) {
    val hasDownload get() = !downloadUrl.isNullOrBlank()
}