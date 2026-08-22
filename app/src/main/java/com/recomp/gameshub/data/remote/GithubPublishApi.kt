package com.recomp.gameshub.data.remote

import com.recomp.gameshub.domain.model.RepoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

@Serializable
data class GithubContentResponse(
    @SerialName("content") val content: String? = null,
    val sha: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

/**
 * Publica/edita arquivos de um repositório GitHub usando a Contents API.
 * Usa o token de acesso do usuário (escopos repo/content).
 */
class GithubPublishApi(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Cria ou atualiza um arquivo em [path] (ex.: "index.json", "games/foo/metadata.json").
     * [content] é enviado como texto; a API codifica em base64.
     */
    suspend fun putFile(
        config: RepoConfig,
        accessToken: String,
        path: String,
        content: String,
        message: String,
    ): GithubContentResponse = withContext(Dispatchers.IO) {
        val base64 = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        val payload = buildString {
            append("{\"message\":").append(json.encodeToString(message))
            append(",\"content\":").append(json.encodeToString(base64))
            append(",\"branch\":").append(json.encodeToString(config.branch))
            append("}")
        }
        val request = Request.Builder()
            .url("https://api.github.com/repos/${config.owner}/${config.repo}/contents/$path")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/vnd.github+json")
            .put(payload.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException(
                    "GitHub respondeu HTTP ${response.code} ao enviar «$path»" +
                        if (body.isNotBlank()) " — ${friendlyError(body)}" else ""
                )
            }
            runCatching { json.decodeFromString<GithubContentResponse>(body) }
                .getOrElse { GithubContentResponse() }
        }
    }

    /** Verifica se o repositório existe e se o token é válido. */
    suspend fun validate(
        config: RepoConfig,
        accessToken: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${config.owner}/${config.repo}")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        "Não foi possível acessar o repositório (HTTP ${response.code}). " +
                            "Confira a URL e o token."
                    )
                }
            }
        }
    }

    private fun friendlyError(body: String): String {
        val lower = body.lowercase()
        return when {
            "not found" in lower || "404" in lower ->
                "repositório ou caminho não encontrado (o arquivo pode precisar ser criado primeiro)"
            "unauthorized" in lower || "bad credentials" in lower ->
                "token inválido ou sem permissão"
            "must have push access" in lower || "403" in lower ->
                "token sem permissão de escrita no repositório"
            "already exists" in lower -> "arquivo já existe (use atualizar)"
            else -> body.take(160)
        }
    }
}
