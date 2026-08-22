package com.recomp.gameshub.data.repository

import com.recomp.gameshub.data.remote.GameMetadataDto
import com.recomp.gameshub.data.remote.GithubPublishApi
import com.recomp.gameshub.data.remote.IndexEntryDto
import com.recomp.gameshub.data.remote.RepoApi
import com.recomp.gameshub.data.remote.RepoIndexDto
import com.recomp.gameshub.data.remote.RepoManifestDto
import com.recomp.gameshub.domain.model.RepoConfig
import com.recomp.gameshub.domain.model.RepoSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

data class LocalGameDraft(
    val slug: String,
    val name: String,
    val description: String = "",
    val originalPlatform: String? = null,
    val author: String? = null,
    val sourceRepo: String? = null,
    val status: String = "released",
    val version: String? = null,
    val downloadUrl: String? = null,
    val fileSizeBytes: Long = 0L,
    val tags: List<String> = emptyList(),
    val lastUpdated: String? = null,
)

/**
 * Gerencia um repositório local de dados do Recomp Hub: cria a estrutura,
 * adiciona jogos próprios e publica tudo no GitHub via [GithubPublishApi].
 *
 * [defaultDir] é a pasta padrão onde o repositório local é criado
 * (normalmente `filesDir/repos/local`).
 */
class LocalRepoRepository(
    private val publishApi: GithubPublishApi,
    private val repoApi: RepoApi,
    val defaultDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val manifestFileName = repoApi.manifestFileName
    private val indexFileName = repoApi.indexFileName

    fun defaultLocalPath(): String = defaultDir.absolutePath

    /** Cria a estrutura base de um repositório local (repo.json + index.json). */
    suspend fun createLocalRepo(dir: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val gamesDir = File(dir, "games")
            if (!gamesDir.exists()) gamesDir.mkdirs()
            val manifest = RepoManifestDto(
                version = "1.0.0",
                name = "Meu Recomp Hub",
                generated = java.time.Instant.now().toString(),
            )
            File(dir, manifestFileName).writeText(json.encodeToString(manifest))
            val index = RepoIndexDto(generated = java.time.Instant.now().toString(), games = emptyList())
            File(dir, indexFileName).writeText(json.encodeToString(index))
        }
    }

    /** Lê o index local; retorna lista vazia se o repositório não existir. */
    suspend fun listLocalGames(dir: File): Result<List<IndexEntryDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val indexFile = File(dir, indexFileName)
            if (!indexFile.exists()) return@runCatching emptyList()
            json.decodeFromString<RepoIndexDto>(indexFile.readText()).games
        }
    }

    /** Adiciona um jogo próprio ao repositório local (metadata.json + index.json). */
    suspend fun addLocalGame(dir: File, draft: LocalGameDraft): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val slug = draft.slug.lowercase().trim()
                .replace(Regex("[^a-z0-9]+"), "-").trim('-')
            if (slug.isEmpty()) throw IllegalArgumentException("Slug inválido.")
            val gameDir = File(dir, "games/$slug")
            if (!gameDir.exists()) gameDir.mkdirs()

            val metadata = GameMetadataDto(
                slug = slug,
                name = draft.name.trim(),
                description = draft.description.trim(),
                originalPlatform = draft.originalPlatform,
                author = draft.author,
                sourceRepo = draft.sourceRepo,
                status = draft.status,
                version = draft.version,
                downloadUrl = draft.downloadUrl,
                fileSizeBytes = draft.fileSizeBytes,
                tags = draft.tags,
                lastUpdated = draft.lastUpdated ?: java.time.LocalDate.now().toString(),
            )
            File(gameDir, "metadata.json").writeText(json.encodeToString(metadata))

            val games = listLocalGames(dir).getOrElse { emptyList() }.toMutableList()
            games.removeAll { it.slug == slug }
            games.add(
                IndexEntryDto(
                    slug = slug,
                    name = metadata.name,
                    status = metadata.status,
                    version = metadata.version,
                    platform = metadata.originalPlatform,
                    cover = metadata.cover,
                    lastUpdated = metadata.lastUpdated,
                )
            )
            games.sortBy { it.name.lowercase() }
            File(dir, indexFileName).writeText(
                json.encodeToString(RepoIndexDto(generated = java.time.Instant.now().toString(), games = games))
            )
        }
    }

    /** Remove um jogo do repositório local. */
    suspend fun removeLocalGame(dir: File, slug: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(dir, "games/$slug").deleteRecursively()
            val games = listLocalGames(dir).getOrElse { emptyList() }.filterNot { it.slug == slug }
            File(dir, indexFileName).writeText(
                json.encodeToString(RepoIndexDto(generated = java.time.Instant.now().toString(), games = games))
            )
        }
    }

    /**
     * Publica o repositório local no GitHub. Envia repo.json, index.json e
     * todos os metadata.json usando a Contents API.
     */
    suspend fun publishToGithub(
        dir: File,
        config: RepoConfig,
        accessToken: String,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            publishApi.validate(config, accessToken).getOrThrow()
            val pushed = mutableListOf<String>()

            val manifest = File(dir, manifestFileName)
            if (manifest.exists()) {
                publishApi.putFile(config, accessToken, manifestFileName, manifest.readText(), "chore: atualiza repo.json")
                pushed += manifestFileName
            }

            val indexFile = File(dir, indexFileName)
            if (indexFile.exists()) {
                publishApi.putFile(config, accessToken, indexFileName, indexFile.readText(), "chore: atualiza index.json")
                pushed += indexFileName
            }

            val gamesDir = File(dir, "games")
            val slugs = gamesDir.listFiles { f -> f.isDirectory }?.map { it.name } ?: emptyList()
            slugs.sorted().forEach { slug ->
                val metadataFile = File(gamesDir, "$slug/metadata.json")
                if (metadataFile.exists()) {
                    val path = "games/$slug/metadata.json"
                    publishApi.putFile(config, accessToken, path, metadataFile.readText(), "feat: adiciona $slug")
                    pushed += path
                }
            }
            pushed
        }
    }

    fun localConfig(localPath: String): RepoConfig = RepoConfig(
        sourceType = RepoSourceType.LOCAL,
        localPath = localPath,
    )
}
