package com.recomp.gameshub.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.recomp.gameshub.domain.model.RepoConfig
import com.recomp.gameshub.domain.model.RepoSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.repoDataStore by preferencesDataStore(name = "repo")

class RepoRepository(private val context: Context) {

    private val sourceTypeKey = stringPreferencesKey("source_type")
    private val ownerKey = stringPreferencesKey("owner")
    private val repoKey = stringPreferencesKey("repo")
    private val branchKey = stringPreferencesKey("branch")
    private val localPathKey = stringPreferencesKey("local_path")
    private val installedVersionKey = stringPreferencesKey("installed_version")

    /** Configuração do repositório; nula enquanto o usuário não concluir o setup. */
    val config: Flow<RepoConfig?> = context.repoDataStore.data.map { prefs ->
        val type = when (prefs[sourceTypeKey]) {
            RepoSourceType.GITHUB_URL.name -> RepoSourceType.GITHUB_URL
            RepoSourceType.LOCAL.name -> RepoSourceType.LOCAL
            else -> RepoSourceType.DEFAULT
        }
        RepoConfig(
            sourceType = type,
            owner = prefs[ownerKey] ?: "",
            repo = prefs[repoKey] ?: "",
            branch = prefs[branchKey] ?: "main",
            localPath = prefs[localPathKey],
        ).takeIf { isConfigured(it) }
    }

    val installedVersion: Flow<String?> = context.repoDataStore.data.map { prefs ->
        prefs[installedVersionKey]
    }

    suspend fun currentConfig(): RepoConfig? = config.first()

    suspend fun currentInstalledVersion(): String? = installedVersion.first()

    suspend fun save(config: RepoConfig) {
        context.repoDataStore.edit { prefs ->
            prefs[sourceTypeKey] = config.sourceType.name
            prefs[ownerKey] = config.owner
            prefs[repoKey] = config.repo
            prefs[branchKey] = config.branch
            if (config.localPath.isNullOrBlank()) prefs.remove(localPathKey)
            else prefs[localPathKey] = config.localPath
        }
    }

    suspend fun saveInstalledVersion(version: String) {
        context.repoDataStore.edit { prefs ->
            prefs[installedVersionKey] = version
        }
    }

    suspend fun isConfigured(): Boolean {
        val cfg = currentConfig() ?: return false
        return isConfigured(cfg)
    }

    private fun isConfigured(cfg: RepoConfig): Boolean =
        when (cfg.sourceType) {
            RepoSourceType.LOCAL -> !cfg.localPath.isNullOrBlank()
            else -> cfg.owner.isNotBlank() && cfg.repo.isNotBlank()
        }
}
