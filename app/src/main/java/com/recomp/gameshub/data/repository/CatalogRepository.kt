package com.recomp.gameshub.data.repository

import com.recomp.gameshub.data.local.GameDao
import com.recomp.gameshub.data.local.toDetail
import com.recomp.gameshub.data.local.toEntity
import com.recomp.gameshub.data.local.toSummary
import com.recomp.gameshub.data.remote.CatalogApi
import com.recomp.gameshub.data.remote.supabase.SupabaseApi
import com.recomp.gameshub.data.remote.supabase.toEntity
import com.recomp.gameshub.domain.model.GameDetail
import com.recomp.gameshub.domain.model.GameSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CatalogRepository(
    private val supabaseApi: SupabaseApi,
    private val githubApi: CatalogApi,
    private val gameDao: GameDao,
) {
    fun observeSummaries(): Flow<List<GameSummary>> =
        gameDao.observeAll().map { list -> list.map { it.toSummary() } }

    fun observeDetail(slug: String): Flow<GameDetail?> =
        gameDao.observeDetail(slug).map { it?.toDetail() }

    /**
     * Refreshes the local catalog from Supabase (approved games, screenshots embedded).
     * Falls back to the GitHub data repository when Supabase is not configured.
     */
    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                val games = supabaseApi.fetchApprovedGames(limit = 30)
                if (games.isEmpty()) {
                    gameDao.clear()
                    return@runCatching 0
                }
                val now = System.currentTimeMillis()
                val entities = games.map { it.toEntity(fetchedAt = now) }
                gameDao.upsertAll(entities)
                gameDao.deleteWhereMissing(games.map { it.slug })
                games.size
            } catch (e: SupabaseApi.NotConfiguredException) {
                refreshFromGithub()
            }
        }
    }

    suspend fun ensureDetail(slug: String) {
        val cached = gameDao.get(slug)
        if (cached == null || cached.fetchedAt == 0L) {
            runCatching { supabaseApi.fetchApprovedGame(slug) }
                .getOrNull()?.let { row ->
                    gameDao.upsert(row.toEntity(fetchedAt = System.currentTimeMillis()))
                }
        }
    }

    suspend fun search(query: String): Result<List<GameSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.trim().length < 2) return@runCatching emptyList()
            supabaseApi.searchApprovedGames(query).map { it.toEntity(0L).toSummary() }
        }
    }

    private suspend fun refreshFromGithub(): Int {
        val index = githubApi.fetchIndex()
        val now = System.currentTimeMillis()
        if (index.isEmpty()) {
            gameDao.clear()
            return 0
        }
        gameDao.upsertAll(index.map { it.toEntity(fetchedAt = 0L) })
        gameDao.deleteWhereMissing(index.map { it.slug })
        index.forEach { entry ->
            runCatching { githubApi.fetchMetadata(entry.slug) }
                .onSuccess { gameDao.upsert(it.toEntity(fetchedAt = now)) }
        }
        return index.size
    }
}
