package com.recomp.gameshub.data.repository

import com.recomp.gameshub.data.local.GameDao
import com.recomp.gameshub.data.local.toDetail
import com.recomp.gameshub.data.local.toSummary
import com.recomp.gameshub.domain.model.GameDetail
import com.recomp.gameshub.domain.model.GameSummary
import com.recomp.gameshub.domain.model.RepoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CatalogRepository(
    private val gameDao: GameDao,
    private val repoManager: RepoManager,
) {
    fun observeSummaries(): Flow<List<GameSummary>> =
        gameDao.observeAll().map { list -> list.map { it.toSummary() } }

    fun observeDetail(slug: String): Flow<GameDetail?> =
        gameDao.observeDetail(slug).map { it?.toDetail() }

    /**
     * Refreshes the local catalog from the configured repository. The first time
     * (or when nothing is configured yet) uses the default repository.
     */
    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        val config = repoManager.config()
        if (config == null) {
            runCatching { repoManager.setupInitial(defaultConfig()) }
        } else {
            repoManager.applyUpdate()
        }
    }

    suspend fun ensureDetail(slug: String) {
        val cached = gameDao.get(slug)
        if (cached == null || cached.fetchedAt == 0L) {
            val config = repoManager.config() ?: defaultConfig()
            runCatching { repoManager.repoApi.fetchMetadata(config, slug) }
                .getOrNull()?.let { row ->
                    gameDao.upsert(row.toEntity(fetchedAt = System.currentTimeMillis()))
                }
        }
    }

    suspend fun search(query: String): Result<List<GameSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.trim().length < 2) return@runCatching emptyList()
            val normalized = query.trim().lowercase()
            gameDao.observeAll().first()
                .map { it.toSummary() }
                .filter { game ->
                    game.name.lowercase().contains(normalized) ||
                        game.originalPlatform?.lowercase()?.contains(normalized) == true ||
                        game.version?.lowercase()?.contains(normalized) == true
                }
                .take(30)
        }
    }

    private fun defaultConfig() = RepoConfig()
}
