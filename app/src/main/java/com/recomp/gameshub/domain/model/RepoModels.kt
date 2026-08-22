package com.recomp.gameshub.domain.model

import com.recomp.gameshub.BuildConfig

/** Tipo de fonte de dados configurada pelo usuário no setup inicial. */
enum class RepoSourceType {
    /** Repositório oficial/padrão do app (BuildConfig.DATA_*). */
    DEFAULT,

    /** Repositório de terceiros informado por URL. */
    GITHUB_URL,

    /** Estrutura de repositório importada localmente do dispositivo. */
    LOCAL,
}

/**
 * Configuração da fonte de dados (repositório) escolhida no onboarding.
 */
data class RepoConfig(
    val sourceType: RepoSourceType = RepoSourceType.DEFAULT,
    val owner: String = BuildConfig.DATA_OWNER,
    val repo: String = BuildConfig.DATA_REPO,
    val branch: String = BuildConfig.DATA_BRANCH,
    val localPath: String? = null,
) {
    val displayName: String
        get() = when (sourceType) {
            RepoSourceType.DEFAULT -> "Repositório oficial ($owner/$repo)"
            RepoSourceType.GITHUB_URL -> "github.com/$owner/$repo"
            RepoSourceType.LOCAL -> localPath?.takeIf { it.isNotBlank() } ?: "Repositório local"
        }

    fun isGithub(): Boolean = sourceType != RepoSourceType.LOCAL
}
