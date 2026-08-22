package com.recomp.gameshub.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomp.gameshub.BuildConfig
import com.recomp.gameshub.data.repository.RepoManager
import com.recomp.gameshub.data.repository.RepoRepository
import com.recomp.gameshub.domain.model.RepoConfig
import com.recomp.gameshub.domain.model.RepoSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SetupStep { PERMISSIONS, REPOSITORY }

sealed interface SetupUiState {
    data object Idle : SetupUiState
    data object Loading : SetupUiState
    data class Error(val message: String) : SetupUiState
    data class Done(val gameCount: Int) : SetupUiState
}

class SetupViewModel(
    private val repoRepository: RepoRepository,
    private val repoManager: RepoManager,
) : ViewModel() {

    private val _step = MutableStateFlow(SetupStep.PERMISSIONS)
    val step: StateFlow<SetupStep> = _step.asStateFlow()

    private val _selectedType = MutableStateFlow(RepoSourceType.DEFAULT)
    val selectedType: StateFlow<RepoSourceType> = _selectedType.asStateFlow()

    private val _customUrl = MutableStateFlow("")
    val customUrl: StateFlow<String> = _customUrl.asStateFlow()

    private val _localPath = MutableStateFlow<String?>(null)
    val localPath: StateFlow<String?> = _localPath.asStateFlow()

    private val _state = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val configured = repoRepository.currentConfig()
            if (configured != null) {
                _selectedType.value = configured.sourceType
                _customUrl.value = githubUrlOf(configured)
                _localPath.value = configured.localPath
            }
        }
    }

    fun nextStep() {
        if (_step.value == SetupStep.PERMISSIONS) {
            _step.value = SetupStep.REPOSITORY
        }
    }

    fun selectType(type: RepoSourceType) {
        _selectedType.value = type
    }

    fun onUrlChanged(value: String) {
        _customUrl.value = value
    }

    fun onLocalPathPicked(path: String) {
        _localPath.value = path
    }

    fun canFinish(): Boolean =
        when (_selectedType.value) {
            RepoSourceType.DEFAULT -> true
            RepoSourceType.GITHUB_URL -> _customUrl.value.trim().isNotBlank()
            RepoSourceType.LOCAL -> !_localPath.value.isNullOrBlank()
        }

    fun buildConfig(): RepoConfig? {
        val type = _selectedType.value
        return when (type) {
            RepoSourceType.DEFAULT -> RepoConfig()
            RepoSourceType.GITHUB_URL -> {
                val (owner, repo, branch) = parseGithubUrl(_customUrl.value.trim())
                    ?: return null
                RepoConfig(
                    sourceType = RepoSourceType.GITHUB_URL,
                    owner = owner,
                    repo = repo,
                    branch = branch,
                )
            }
            RepoSourceType.LOCAL -> RepoConfig(
                sourceType = RepoSourceType.LOCAL,
                localPath = _localPath.value,
            )
        }
    }

    fun finish() {
        if (_state.value is SetupUiState.Loading) return
        val config = buildConfig() ?: run {
            _state.value = SetupUiState.Error("Verifique a URL do repositório.")
            return
        }
        _state.value = SetupUiState.Loading
        viewModelScope.launch {
            repoRepository.save(config)
            repoManager.setupInitial(config)
                .onSuccess { count ->
                    _state.value = SetupUiState.Done(gameCount = count)
                }
                .onFailure { failure ->
                    _state.value = SetupUiState.Error(
                        failure.message ?: "Não foi possível baixar o repositório."
                    )
                }
        }
    }

    fun resetError() {
        if (_state.value is SetupUiState.Error) _state.value = SetupUiState.Idle
    }

    private fun githubUrlOf(config: RepoConfig): String =
        if (config.sourceType == RepoSourceType.GITHUB_URL) {
            "https://github.com/${config.owner}/${config.repo}"
        } else {
            ""
        }

    companion object {
        /** Converte uma URL do GitHub em (owner, repo, branch). Suporta branches via URL. */
        fun parseGithubUrl(url: String): Triple<String, String, String>? {
            val trimmed = url.trim().removeSuffix("/")
                .replace(".git", "")
            val match = Regex(
                "https?://github\\.com/([^/]+)/([^/]+)(?:/tree/([^/]+))?"
            ).matchEntire(trimmed) ?: return null
            val owner = match.groupValues[1].takeIf { it.isNotBlank() } ?: return null
            val repo = match.groupValues[2].takeIf { it.isNotBlank() } ?: return null
            val branch = match.groupValues[3].takeIf { it.isNotBlank() } ?: BuildConfig.DATA_BRANCH
            return Triple(owner, repo, branch)
        }
    }
}
