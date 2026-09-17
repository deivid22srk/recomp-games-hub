package com.recomp.gameshub.core.util

/**
 * Utilitários centralizados para URLs de repositórios GitHub e para validação
 * de slugs de jogos usados em caminhos de arquivo.
 */
object GitHubUrls {

    private val repoPattern = Regex(
        "^https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/tree/([^/]+))?/?$"
    )

    private val slugPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

    data class ParsedRepo(val owner: String, val repo: String, val branch: String?)

    /**
     * Converte uma URL de repositório GitHub em (owner, repo, branch opcional).
     * Suporta sufixo ".git" e indicação de branch via "/tree/<branch>".
     * Retorna null quando a URL não é um repositório válido.
     */
    fun parseRepoUrl(url: String): ParsedRepo? {
        val match = repoPattern.matchEntire(url.trim()) ?: return null
        val owner = match.groupValues[1].takeIf { it.isNotBlank() } ?: return null
        val repo = match.groupValues[2].takeIf { it.isNotBlank() } ?: return null
        val branch = match.groupValues[3].takeIf { it.isNotBlank() }
        return ParsedRepo(owner, repo, branch)
    }

    /** Extrai apenas (owner, repo); branch é ignorado. */
    fun parseOwnerRepo(url: String): Pair<String, String>? =
        parseRepoUrl(url)?.let { it.owner to it.repo }

    /**
     * Um slug é usado como nome de diretório (games/<slug>/) e em URLs raw.
     * Apenas letras, números, ponto, hífen e underline são permitidos, sem
     * ".." e sem separadores de caminho, evitando path traversal.
     */
    fun isValidSlug(slug: String): Boolean =
        slug.length <= 128 && !slug.contains("..") && slugPattern.matches(slug)

    /** Normaliza e valida um slug; retorna null quando inválido. */
    fun sanitizeSlug(raw: String): String? =
        raw.trim().takeIf { isValidSlug(it) }
}
