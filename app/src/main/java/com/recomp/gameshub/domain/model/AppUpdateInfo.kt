package com.recomp.gameshub.domain.model

data class AppUpdateInfo(
    val id: String?,
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val notes: String?,
    val publishedAt: String? = null,
)
