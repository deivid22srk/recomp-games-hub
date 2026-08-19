package com.recomp.gameshub.domain.model

enum class GameStatus(val label: String) {
    RELEASED("Disponível"),
    BETA("Beta"),
    ALPHA("Alpha"),
    IN_DEVELOPMENT("Em desenvolvimento"),
    UNKNOWN("—");

    companion object {
        fun from(raw: String?): GameStatus =
            when (raw?.trim()?.lowercase()) {
                "released", "stable", "available", "complete" -> RELEASED
                "beta" -> BETA
                "alpha" -> ALPHA
                "in-development", "wip", "development", "in_development" -> IN_DEVELOPMENT
                else -> UNKNOWN
            }
    }
}

data class GameSummary(
    val slug: String,
    val name: String,
    val status: GameStatus,
    val version: String?,
    val originalPlatform: String?,
    val cover: String?,
    val lastUpdated: String?,
)