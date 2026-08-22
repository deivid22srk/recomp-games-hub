package com.recomp.gameshub.presentation.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomp.gameshub.data.repository.LocalGameDraft
import com.recomp.gameshub.data.repository.LocalRepoRepository
import com.recomp.gameshub.data.repository.RepoRepository
import com.recomp.gameshub.domain.model.RepoConfig
import com.recomp.gameshub.domain.model.RepoSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface RepoEditorState {
    data object Idle : RepoEditorState
    data object Loading : RepoEditorState
    data class Error(val message: String) : RepoEditorState
    data class Success(val message: String) : RepoEditorState
}

class RepoEditorViewModel(
    private val repoRepository: RepoRepository,
    private val localRepoRepository: LocalRepoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<RepoEditorState>(RepoEditorState.Idle)
    val state: StateFlow<RepoEditorState> = _state.asStateFlow()

    private val _localGames = MutableStateFlow<List<String>>(emptyList())
    val localGames: StateFlow<List<String>> = _localGames.asStateFlow()

    private val _localPath = MutableStateFlow(localRepoRepository.defaultLocalPath())
    val localPath: StateFlow<String> = _localPath.asStateFlow()

    val repoConfig: StateFlow<RepoConfig?> = repoRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    init {
        loadLocalRepo()
    }

    private fun loadLocalRepo() {
        viewModelScope.launch {
            val cfg = repoRepository.currentConfig()
            if (cfg?.sourceType == RepoSourceType.LOCAL && !cfg.localPath.isNullOrBlank()) {
                _localPath.value = cfg.localPath
            }
            refreshLocalGames()
        }
    }

    /** Cria o repositório local (estrutura base) e o define como fonte ativa. */
    fun createLocalRepo() {
        if (_state.value is RepoEditorState.Loading) return
        viewModelScope.launch {
            _state.value = RepoEditorState.Loading
            val dir = File(_localPath.value)
            localRepoRepository.createLocalRepo(dir)
                .onSuccess {
                    val config = RepoConfig(
                        sourceType = RepoSourceType.LOCAL,
                        localPath = dir.absolutePath,
                    )
                    repoRepository.save(config)
                    _localPath.value = dir.absolutePath
                    refreshLocalGames()
                    _state.value = RepoEditorState.Success("Repositório local criado com sucesso.")
                }
                .onFailure {
                    _state.value = RepoEditorState.Error(it.message ?: "Falha ao criar repositório local.")
                }
        }
    }

    /** Adiciona um jogo próprio ao repositório local. */
    fun addGame(draft: LocalGameDraft) {
        if (_state.value is RepoEditorState.Loading) return
        viewModelScope.launch {
            _state.value = RepoEditorState.Loading
            localRepoRepository.addLocalGame(File(_localPath.value), draft)
                .onSuccess {
                    refreshLocalGames()
                    _state.value = RepoEditorState.Success("«${draft.name}» adicionado ao repositório.")
                }
                .onFailure {
                    _state.value = RepoEditorState.Error(it.message ?: "Falha ao adicionar o jogo.")
                }
        }
    }

    /** Remove um jogo do repositório local. */
    fun removeGame(slug: String) {
        viewModelScope.launch {
            localRepoRepository.removeLocalGame(File(_localPath.value), slug)
                .onSuccess {
                    refreshLocalGames()
                    _state.value = RepoEditorState.Success("Jogo removido.")
                }
                .onFailure {
                    _state.value = RepoEditorState.Error(it.message ?: "Falha ao remover o jogo.")
                }
        }
    }

    /** Publica o repositório local no GitHub informando URL + token. */
    fun publishToGithub() {
        if (_state.value is RepoEditorState.Loading) return
        val url = _url.value.trim()
        val token = _token.value.trim()
        if (url.isEmpty() || token.isEmpty()) {
            _state.value = RepoEditorState.Error("Informe a URL do repositório e o token de acesso.")
            return
        }
        val config = parseRepoUrl(url)
        if (config == null) {
            _state.value = RepoEditorState.Error("URL de repositório inválida. Use https://github.com/usuario/repo")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = RepoEditorState.Loading
            localRepoRepository.publishToGithub(File(_localPath.value), config, token)
                .onSuccess { files ->
                    _state.value = RepoEditorState.Success(
                        "Publicado com sucesso (${files.size} arquivo(s)) em github.com/${config.owner}/${config.repo}."
                    )
                }
                .onFailure {
                    _state.value = RepoEditorState.Error(it.message ?: "Falha ao publicar no GitHub.")
                }
        }
    }

    fun onUrlChanged(value: String) { _url.value = value }
    fun onTokenChanged(value: String) { _token.value = value }
    fun dismissState() { _state.value = RepoEditorState.Idle }

    private fun refreshLocalGames() {
        viewModelScope.launch {
            localRepoRepository.listLocalGames(File(_localPath.value))
                .onSuccess { _localGames.value = it.map { g -> g.name } }
        }
    }

    companion object {
        fun parseRepoUrl(url: String): RepoConfig? {
            val match = Regex("https?://github\\.com/([^/]+)/([^/]+)")
                .find(url.trim().removeSuffix("/"))
                ?: return null
            return RepoConfig(
                sourceType = RepoSourceType.GITHUB_URL,
                owner = match.groupValues[1],
                repo = match.groupValues[2].removeSuffix(".git"),
                branch = "main",
            )
        }
    }
}
