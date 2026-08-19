package com.recomp.gameshub.data.repository

import com.recomp.gameshub.data.local.GameDao
import com.recomp.gameshub.data.local.toEntity
import com.recomp.gameshub.data.local.toDetail
import com.recomp.gameshub.data.local.toSummary
import com.recomp.gameshub.data.remote.CatalogApi
import com.recomp.gameshub.domain.model.GameDetail
import com.recomp.gameshub.domain.model.GameSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CatalogRepository(
    private val api: CatalogApi,
    private val gameDao: GameDao,
) {
    fun observeSummaries(): Flow<List<GameSummary>> =
        gameDao.observeAll().map { list -> list.map { it.toSummary() } }

    fun observeDetail(slug: String): Flow<GameDetail?> =
        gameDao.observeDetail(slug).map { it?.toDetail() }

    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val index = api.fetchIndex()
            val now = System.currentTimeMillis()
            if (index.isEmpty()) {
                gameDao.clear()
                return@runCatching 0
            }
            gameDao.upsertAll(index.map { it.toEntity(fetchedAt = 0L) })
            gameDao.deleteWhereMissing(index.map { it.slug })

            coroutineScope {
                index.map { entry ->
                    async {
                        runCatching { api.fetchMetadata(entry.slug) }.onSuccess { meta ->
                            gameDao.upsert(meta.toEntity(fetchedAt = now))
                        }
                    }
                }.awaitAll()
            }
            index.size
        }
    }

    suspend fun ensureDetail(slug: String) {
        val cached = gameDao.get(slug)
        if (cached == null || cached.fetchedAt == 0L) {
            runCatching { api.fetchMetadata(slug) }
                .onSuccess { gameDao.upsert(it.toEntity(fetchedAt = System.currentTimeMillis())) }
        }
    }
}