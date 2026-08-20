package com.recomp.gameshub.domain.model

data class GameSubmission(
    val submissionId: String,
    val slug: String,
    val name: String,
    val status: String,
    val devStatus: String = "in_development",
    val version: String?,
    val description: String,
    val originalPlatform: String?,
    val author: String?,
    val sourceRepo: String?,
    val apkUrl: String?,
    val fileSizeBytes: Long,
    val tags: List<String>,
    val coverUrl: String?,
    val bannerUrl: String?,
    val screenshots: List<String> = emptyList(),
    val reviewReason: String? = null,
    val submittedAt: String? = null,
)

enum class SubmissionStatus(val label: String) {
    PENDING("Pendente de revisão"),
    APPROVED("Aprovado e publicado"),
    REJECTED("Rejeitado"),
}
