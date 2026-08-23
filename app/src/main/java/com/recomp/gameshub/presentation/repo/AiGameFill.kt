package com.recomp.gameshub.presentation.repo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Resposta esperada do prompt de preenchimento por IA.
 */
@Serializable
data class AiGameFill(
    val name: String? = null,
    val description: String? = null,
    val platform: String? = null,
    val author: String? = null,
    val repository: String? = null,
    val tags: List<String>? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    val screenshots: List<String>? = null,
)

/**
 * Monta o prompt que será copiado para a área de transferência.
 * Substitui "URL do repositório" pela URL informada pelo usuário.
 */
fun buildAiPrompt(repositoryUrl: String): String =
    """
    Você é um agente de pesquisa rigoroso. Acesse e investigue este repositório do GitHub: "$repositoryUrl"
    Obtenha automaticamente, usando o conteúdo do repositório e as releases, os dados da recompilação Android: nome do jogo (pode ser o nome da recomp), descrição, plataforma original, autor da recompilação, URL exata do repositório, tags, URL da capa, URL do banner e URLs diretas de screenshots (se existirem). Não invente informações: use null ou [] quando não encontrar. Retorne SOMENTE um JSON válido, sem markdown, exatamente neste formato:
    {"name":"","description":"","platform":"","author":"","repository":"$repositoryUrl","tags":[],"cover_url":null,"banner_url":null,"screenshots":[]}
    """.trimIndent()

/**
 * Faz o parse do JSON de resposta da IA. Retorna erro com mensagem amigável
 * quando o JSON é inválido; campos ausentes/null viram valores vazios.
 */
fun parseAiFill(raw: String): Result<AiGameFill> {
    val block = Regex("```(?:[a-zA-Z]*)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.get(1)
    var candidate = (block ?: raw).trim()
    // Fallback: extrair do primeiro "{" ao último "}" (cobre texto antes/depois).
    val firstBrace = candidate.indexOf('{')
    val lastBrace = candidate.lastIndexOf('}')
    if (firstBrace >= 0 && lastBrace > firstBrace) {
        candidate = candidate.substring(firstBrace, lastBrace + 1)
    }
    return runCatching {
        Json { ignoreUnknownKeys = true; coerceInputValues = true }
            .decodeFromString<AiGameFill>(candidate)
    }.mapError { error ->
        "JSON inválido. Verifique se a resposta da IA está no formato pedido.\n\n${error.message ?: "Erro de parsing"}"
    }
}

private fun <T> Result<T>.mapError(f: (Throwable) -> String): Result<T> =
    this.fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(Exception(f(it))) })
