package com.recomp.gameshub.core.util

import com.recomp.gameshub.BuildConfig

/**
 * Resolução centralizada de URLs de imagens do catálogo (capa, banner,
 * screenshots), usada por todas as telas.
 *
 * Regras:
 * - URL http(s) direta é usada como está;
 * - URL "blob" do GitHub é convertida para "raw.githubusercontent.com";
 * - caminho relativo é resolvido contra o repositório de dados padrão.
 */
object CoverResolver {

    private val blobPattern = Regex(
        "https?://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)"
    )

    fun resolve(
        slug: String,
        value: String?,
        owner: String = BuildConfig.DATA_OWNER,
        repo: String = BuildConfig.DATA_REPO,
        branch: String = BuildConfig.DATA_BRANCH,
    ): String? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        blobPattern.matchEntire(trimmed)?.let { match ->
            return "https://raw.githubusercontent.com/" +
                "${match.groupValues[1]}/${match.groupValues[2]}/" +
                "${match.groupValues[3]}/${match.groupValues[4]}"
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "https://raw.githubusercontent.com/$owner/$repo/$branch/games/$slug/$trimmed"
    }
}
