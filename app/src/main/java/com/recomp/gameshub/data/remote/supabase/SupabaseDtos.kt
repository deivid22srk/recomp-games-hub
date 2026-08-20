package com.recomp.gameshub.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val GAMES_TABLE = "games"
const val SCREENSHOTS_TABLE = "game_screenshots"
const val PROFILES_TABLE = "profiles"

const val REVIEW_PENDING = "pending"
const val REVIEW_APPROVED = "approved"
const val REVIEW_REJECTED = "rejected"

@Serializable
data class ScreenshotEmbed(
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class GameRow(
    val id: String? = null,
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("original_platform") val originalPlatform: String? = null,
    val status: String = "",
    val version: String? = null,
    val author: String? = null,
    @SerialName("source_repo_url") val sourceRepoUrl: String? = null,
    @SerialName("apk_url") val apkUrl: String? = null,
    @SerialName("file_size_bytes") val fileSizeBytes: Long = 0L,
    @SerialName("sha256") val sha256: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    @SerialName("submitted_by") val submittedBy: String? = null,
    @SerialName("review_status") val reviewStatus: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("review_reason") val reviewReason: String? = null,
    @SerialName("game_screenshots") val screenshots: List<ScreenshotEmbed>? = null,
)

@Serializable
data class ProfileRow(
    val id: String? = null,
    val email: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AuthSessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: AuthUserDto,
)

@Serializable
data class AuthUserDto(
    val id: String,
    val email: String? = null,
)

@Serializable
data class PromoteAdminDto(
    val ok: Boolean = false,
    val code: String = "",
    val message: String? = null,
)