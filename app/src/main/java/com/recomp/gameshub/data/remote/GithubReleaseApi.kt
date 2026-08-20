package com.recomp.gameshub.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class GithubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L,
)

@Serializable
data class GithubReleaseDto(
    val id: Long = 0L,
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val author: GithubAuthorDto? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GithubAssetDto> = emptyList(),
)

@Serializable
data class GithubAuthorDto(val login: String = "")

data class GithubRelease(
    val id: Long,
    val version: String,
    val author: String?,
    val publishedAt: String?,
    val apk: GithubAssetDto,
)

class GithubReleaseApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchReleases(repositoryUrl: String): List<GithubRelease> = withContext(Dispatchers.IO) {
        val (owner, repo) = parseRepository(repositoryUrl)
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases?per_page=100")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Não foi possível carregar as releases do GitHub (HTTP ${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            json.decodeFromString<List<GithubReleaseDto>>(body)
                .asSequence()
                .filter { it.assets.isNotEmpty() }
                .mapNotNull { release ->
                    val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                        ?: return@mapNotNull null
                    GithubRelease(
                        id = release.id,
                        version = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
                        author = release.author?.login?.takeIf { it.isNotBlank() },
                        publishedAt = release.publishedAt,
                        apk = apk,
                    )
                }
                .toList()
        }
    }

    suspend fun latest(repositoryUrl: String): GithubRelease =
        fetchReleases(repositoryUrl).firstOrNull()
            ?: throw IOException("O repositório não possui uma release publicada com arquivo APK.")

    companion object {
        fun parseRepository(repositoryUrl: String): Pair<String, String> {
            val value = repositoryUrl.trim().removeSuffix("/").removeSuffix(".git")
            val match = Regex("^https?://github\\.com/([^/]+)/([^/]+)(?:/.*)?$").matchEntire(value)
                ?: throw IOException("Informe uma URL válida de um repositório do GitHub.")
            return match.groupValues[1] to match.groupValues[2]
        }
    }
}
