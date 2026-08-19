package com.recomp.gameshub.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class IndexEntryDto(
    val slug: String,
    val name: String,
    val status: String? = null,
    val version: String? = null,
    val platform: String? = null,
    val cover: String? = null,
    val lastUpdated: String? = null,
)

@Serializable
data class GameMetadataDto(
    val slug: String,
    val name: String,
    val description: String = "",
    val originalPlatform: String? = null,
    val author: String? = null,
    val sourceRepo: String? = null,
    val status: String? = null,
    val version: String? = null,
    val downloadUrl: String? = null,
    val fileSizeBytes: Long = 0L,
    val sha256: String? = null,
    val tags: List<String> = emptyList(),
    val lastUpdated: String? = null,
    val banner: String? = null,
    val cover: String? = null,
    val screenshots: List<String> = emptyList(),
)