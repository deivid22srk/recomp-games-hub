package com.recomp.gameshub.presentation.repo

import com.recomp.gameshub.data.repository.LocalGameDraft
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
    val trimmed = raw.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```")
        .trim()
    return runCatching {
        Json { ignoreUnknownKeys = true; coerceInputValues = true }
            .decodeFromString<AiGameFill>(trimmed)
    }.mapError { error ->
        "JSON inválido. Verifique se a resposta da IA está no formato pedido.\n\n${error.message ?: "Erro de parsing"}"
    }
}

fun AiGameFill.toDraft(slug: String): LocalGameDraft = LocalGameDraft(
    slug = slug,
    name = name.orEmpty(),
    description = description.orEmpty(),
    originalPlatform = platform?.takeIf { it.isNotBlank() },
    author = author?.takeIf { it.isNotBlank() },
    sourceRepo = repository?.takeIf { it.isNotBlank() },
    version = null,
    downloadUrl = null,
    fileSizeBytes = 0L,
    tags = tags.orEmpty(),
    coverUrl = coverUrl?.takeIf { it.isNotBlank() },
    bannerUrl = bannerUrl?.takeIf { it.isNotBlank() },
    screenshots = screenshots.orEmpty().filter { it.isNotBlank() },
)

private fun <T> Result<T>.mapError(f: (Throwable) -> String): Result<T> =
    this.fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(Exception(f(it))) })
