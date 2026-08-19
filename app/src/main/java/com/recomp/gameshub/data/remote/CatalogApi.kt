package com.recomp.gameshub.data.remote

import com.recomp.gameshub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class IndexDto(
    val generated: String? = null,
    val games: List<IndexEntryDto> = emptyList(),
)

class CatalogApi(
    private val client: OkHttpClient,
    private val owner: String = BuildConfig.DATA_OWNER,
    private val repo: String = BuildConfig.DATA_REPO,
    private val branch: String = BuildConfig.DATA_BRANCH,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchIndex(): List<IndexEntryDto> =
        getFromRaw("index.json") { body -> json.decodeFromString<IndexDto>(body).games }

    suspend fun fetchMetadata(slug: String): GameMetadataDto =
        getFromRaw("games/$slug/metadata.json") { body -> json.decodeFromString<GameMetadataDto>(body) }

    private suspend fun <T> getFromRaw(path: String, transform: (String) -> T): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(rawUrl(path)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = response.message
                    throw IOException(
                        "HTTP ${response.code} ao carregar «$path»${if (message.isNotBlank()) " — $message" else ""}"
                    )
                }
                val body = response.body?.string() ?: throw IOException("Resposta vazia para «$path»")
                try {
                    transform(body)
                } catch (e: SerializationException) {
                    throw IOException("JSON inválido em «$path»", e)
                }
            }
        }

    private fun rawUrl(path: String): String =
        "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"

    fun resolvePath(slug: String, rel: String?): String? =
        if (rel.isNullOrBlank()) null else rawUrl("games/$slug/$rel")
}