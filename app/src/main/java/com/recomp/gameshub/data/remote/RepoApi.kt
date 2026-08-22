package com.recomp.gameshub.data.remote

import com.recomp.gameshub.domain.model.RepoConfig
import com.recomp.gameshub.domain.model.RepoSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
data class RepoManifestDto(
    val version: String = "",
    val generated: String? = null,
    val name: String? = null,
)

@Serializable
data class RepoIndexDto(
    val generated: String? = null,
    val games: List<IndexEntryDto> = emptyList(),
)

/**
 * Lê dados de um repositório de dados do Recomp Hub.
 *
 * Para fontes GitHub ([RepoConfig.isGithub]) busca via raw.githubusercontent.
 * Para fontes locais lê da pasta informada em [RepoConfig.localPath].
 */
class RepoApi(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val manifestFileName = "repo.json"
    val indexFileName = "index.json"

    /** Manifesto (repo.json) contendo a versão atual do repositório. */
    suspend fun fetchManifest(config: RepoConfig): RepoManifestDto =
        readJson(config, manifestFileName) { body -> json.decodeFromString<RepoManifestDto>(body) }

    suspend fun fetchIndex(config: RepoConfig): List<IndexEntryDto> =
        readJson(config, indexFileName) { body ->
            json.decodeFromString<RepoIndexDto>(body).games
        }

    suspend fun fetchMetadata(config: RepoConfig, slug: String): GameMetadataDto =
        readJson(config, "games/$slug/metadata.json") { body ->
            json.decodeFromString<GameMetadataDto>(body)
        }

    /** Resolve um caminho relativo (ex.: banner.png) para uma URL/arquivo utilizável. */
    fun resolve(config: RepoConfig, slug: String, rel: String?): String? {
        if (rel.isNullOrBlank()) return null
        if (rel.startsWith("http://") || rel.startsWith("https://")) return rel
        return if (config.sourceType == RepoSourceType.LOCAL) {
            config.localPath?.let { base ->
                File(base, "games/$slug/$rel").let { if (it.exists()) it.absolutePath else null }
            }
        } else {
            "https://raw.githubusercontent.com/${config.owner}/${config.repo}/${config.branch}/games/$slug/$rel"
        }
    }

    private suspend fun <T> readJson(config: RepoConfig, path: String, transform: (String) -> T): T =
        withContext(Dispatchers.IO) {
            if (config.sourceType == RepoSourceType.LOCAL) {
                val base = config.localPath ?: throw IOException("Pasta do repositório local não definida.")
                val file = File(base, path)
                if (!file.exists()) throw IOException("Arquivo não encontrado no repositório local: $path")
                val body = file.readText()
                try {
                    transform(body)
                } catch (e: Exception) {
                    throw IOException("JSON inválido em «$path»", e)
                }
            } else {
                val request = Request.Builder().url(rawUrl(config, path)).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException(
                            "HTTP ${response.code} ao carregar «$path»" +
                                if (response.message.isNotBlank()) " — ${response.message}" else ""
                        )
                    }
                    val body = response.body?.string() ?: throw IOException("Resposta vazia para «$path»")
                    try {
                        transform(body)
                    } catch (e: Exception) {
                        throw IOException("JSON inválido em «$path»", e)
                    }
                }
            }
        }

    private fun rawUrl(config: RepoConfig, path: String): String =
        "https://raw.githubusercontent.com/${config.owner}/${config.repo}/${config.branch}/$path"
}
