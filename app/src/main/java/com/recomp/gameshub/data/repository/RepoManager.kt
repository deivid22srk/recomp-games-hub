package com.recomp.gameshub.data.repository

import com.recomp.gameshub.data.local.GameDao
import com.recomp.gameshub.data.local.toEntity
import com.recomp.gameshub.data.remote.RepoApi
import com.recomp.gameshub.domain.model.AppVersions
import com.recomp.gameshub.domain.model.RepoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface RepoUpdateInfo {
    data class Available(
        val version: String,
        val installedVersion: String?,
    ) : RepoUpdateInfo

    data object UpToDate : RepoUpdateInfo
}

class RepoManager(
    private val repoRepository: RepoRepository,
    val repoApi: RepoApi,
    private val gameDao: GameDao,
) {

    suspend fun config(): RepoConfig? = repoRepository.currentConfig()

    suspend fun installedVersion(): String? = repoRepository.currentInstalledVersion()

    /**
     * Primeira configuração: baixa o índice do repositório e as metadatas de
     * cada jogo e alimenta o catálogo local. Grava a versão instalada.
     */
    suspend fun setupInitial(config: RepoConfig): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = repoApi.fetchManifest(config)
            syncCatalog(config)
            repoRepository.saveInstalledVersion(manifest.version)
            gameDao.observeAll().first().size
        }
    }

    /**
     * Verifica se há versão mais nova no repositório remoto comparada à versão
     * salva localmente. Retorna [RepoUpdateInfo.UpToDate] quando não há mudança,
     * garantindo que o diálogo só apareça quando existir atualização real.
     */
    suspend fun checkForUpdate(): Result<RepoUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val config = repoRepository.currentConfig()
                ?: return@runCatching RepoUpdateInfo.UpToDate
            val remote = repoApi.fetchManifest(config).version
            if (remote.isBlank()) return@runCatching RepoUpdateInfo.UpToDate
            val local = repoRepository.currentInstalledVersion()
            if (local.isNullOrBlank() || AppVersions.compare(local, remote) < 0) {
                RepoUpdateInfo.Available(version = remote, installedVersion = local)
            } else {
                RepoUpdateInfo.UpToDate
            }
        }
    }

    /** Baixa a nova versão e substitui/atualiza os dados locais do catálogo. */
    suspend fun applyUpdate(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val config = repoRepository.currentConfig()
                ?: throw IllegalStateException("Repositório não configurado.")
            val manifest = repoApi.fetchManifest(config)
            syncCatalog(config)
            repoRepository.saveInstalledVersion(manifest.version)
            gameDao.observeAll().first().size
        }
    }

    private suspend fun syncCatalog(config: RepoConfig) {
        val index = repoApi.fetchIndex(config)
        val now = System.currentTimeMillis()
        if (index.isEmpty()) {
            gameDao.clear()
        } else {
            gameDao.upsertAll(index.map { it.toEntity(fetchedAt = 0L) })
            gameDao.deleteWhereMissing(index.map { it.slug })
            index.forEach { entry ->
                runCatching { repoApi.fetchMetadata(config, entry.slug) }
                    .onSuccess { gameDao.upsert(it.toEntity(fetchedAt = now)) }
            }
        }
    }
}
