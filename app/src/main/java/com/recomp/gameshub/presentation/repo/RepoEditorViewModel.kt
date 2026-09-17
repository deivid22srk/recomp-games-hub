package com.recomp.gameshub.presentation.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomp.gameshub.data.remote.GameMetadataDto
import com.recomp.gameshub.data.repository.CatalogRepository
import com.recomp.gameshub.data.repository.LocalGameDraft
import com.recomp.gameshub.data.repository.LocalRepoRepository
import com.recomp.gameshub.data.repository.RepoManager
import com.recomp.gameshub.data.repository.RepoRepository
import com.recomp.gameshub.domain.model.GameSummary
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

enum class RepoSection { HOME, CONTINUE, CREATE_LOCAL, MANAGE_GAMES, PUBLISH }

sealed interface RepoOpState {
    data object Idle : RepoOpState
    data object Loading : RepoOpState
    data class Error(val message: String) : RepoOpState
    data class Success(val message: String) : RepoOpState
}

class RepoEditorViewModel(
    private val repoRepository: RepoRepository,
    private val localRepoRepository: LocalRepoRepository,
    private val repoManager: RepoManager,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _section = MutableStateFlow(RepoSection.HOME)
    val section: StateFlow<RepoSection> = _section.asStateFlow()

    private val _state = MutableStateFlow<RepoOpState>(RepoOpState.Idle)
    val state: StateFlow<RepoOpState> = _state.asStateFlow()

    val repoConfig: StateFlow<RepoConfig?> = repoRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val summaries: StateFlow<List<GameSummary>> = catalogRepository.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // CONTINUE section
    private val _continueUrl = MutableStateFlow("")
    val continueUrl: StateFlow<String> = _continueUrl.asStateFlow()

    // PUBLISH section
    private val _publishUrl = MutableStateFlow("")
    val publishUrl: StateFlow<String> = _publishUrl.asStateFlow()
    private val _publishToken = MutableStateFlow("")
    val publishToken: StateFlow<String> = _publishToken.asStateFlow()

    // MANAGE GAMES
    private val _editingSlug = MutableStateFlow<String?>(null)
    val editingSlug: StateFlow<String?> = _editingSlug.asStateFlow()
    private val _editingDraft = MutableStateFlow<LocalGameDraft?>(null)
    val editingDraft: StateFlow<LocalGameDraft?> = _editingDraft.asStateFlow()
    private val _addingForm = MutableStateFlow(false)
    val addingForm: StateFlow<Boolean> = _addingForm.asStateFlow()

    fun navigateTo(section: RepoSection) { _section.value = section }

    // --- CONTINUE ---
    fun continueWithDefault() {
        _state.value = RepoOpState.Loading
        viewModelScope.launch {
            repoManager.materializeRemoteToLocal(RepoConfig(), localRepoRepository.defaultDir)
                .onSuccess {
                    repoRepository.save(localRepoRepository.localConfig(localRepoRepository.defaultLocalPath()))
                    repoManager.syncFromLocal()
                    _state.value = RepoOpState.Success("Repositório oficial ativado (${it} jogos).")
                }
                .onFailure {
                    _state.value = RepoOpState.Error(it.message ?: "Falha ao baixar repositório.")
                }
        }
    }

    fun continueWithUrl(url: String) {
        val parsed = parseRepoUrl(url)
        if (parsed == null) {
            _state.value = RepoOpState.Error("URL inválida. Use https://github.com/usuario/repo")
            return
        }
        _state.value = RepoOpState.Loading
        viewModelScope.launch {
            repoManager.materializeRemoteToLocal(parsed, localRepoRepository.defaultDir)
                .onSuccess {
                    repoRepository.save(localRepoRepository.localConfig(localRepoRepository.defaultLocalPath()))
                    repoManager.syncFromLocal()
                    _state.value = RepoOpState.Success("Repositório ativado (${it} jogos).")
                }
                .onFailure {
                    _state.value = RepoOpState.Error(it.message ?: "Falha ao baixar repositório.")
                }
        }
    }

    fun onContinueUrlChanged(v: String) { _continueUrl.value = v }

    // --- CREATE LOCAL ---
    fun createLocalRepo() {
        _state.value = RepoOpState.Loading
        viewModelScope.launch {
            localRepoRepository.createLocalRepo(localRepoRepository.defaultDir)
                .onSuccess {
                    repoRepository.save(localRepoRepository.localConfig(localRepoRepository.defaultLocalPath()))
                    repoManager.syncFromLocal()
                    repoManager.recordInstalledVersion("1.0.0")
                    _state.value = RepoOpState.Success("Repositório local criado e ativado.")
                }
                .onFailure {
                    _state.value = RepoOpState.Error(it.message ?: "Falha ao criar repositório local.")
                }
        }
    }

    // --- MANAGE GAMES ---
    fun startEdit(slug: String) {
        _state.value = RepoOpState.Idle
        viewModelScope.launch {
            _editingSlug.value = slug
            val result = localRepoRepository.getLocalGame(localRepoRepository.defaultDir, slug)
            val meta = result.getOrNull()
            if (meta == null) {
                _editingSlug.value = null
                _state.value = RepoOpState.Error(
                    result.exceptionOrNull()?.message
                        ?: "Não foi possível carregar os dados deste jogo. Baixe um repositório local primeiro."
                )
                return@launch
            }
            _editingDraft.value = LocalGameDraft(
                slug = meta.slug,
                name = meta.name,
                description = meta.description,
                originalPlatform = meta.originalPlatform,
                author = meta.author,
                sourceRepo = meta.sourceRepo,
                status = meta.status ?: "released",
                version = meta.version,
                downloadUrl = meta.downloadUrl,
                fileSizeBytes = meta.fileSizeBytes,
                tags = meta.tags,
                lastUpdated = meta.lastUpdated,
                coverUrl = meta.cover,
                bannerUrl = meta.banner,
                screenshots = meta.screenshots,
            )
        }
    }

    fun saveEdit(slug: String, draft: LocalGameDraft) {
        _state.value = RepoOpState.Loading
        viewModelScope.launch {
            localRepoRepository.updateLocalGame(localRepoRepository.defaultDir, slug, draft)
                .onSuccess {
                    repoManager.syncFromLocal()
                    _editingSlug.value = null
                    _editingDraft.value = null
                    _state.value = RepoOpState.Success("«${draft.name}» atualizado.")
                }
                .onFailure {
                    _state.value = RepoOpState.Error(it.message ?: "Falha ao atualizar jogo.")
                }
        }
    }

    fun addGame(draft: LocalGameDraft) {
        _state.value = RepoOpState.Loading
        viewModelScope.launch {
            localRepoRepository.addLocalGame(localRepoRepository.defaultDir, draft)
                .onSuccess {
                    repoManager.syncFromLocal()
                    _addingForm.value = false
                    _state.value = RepoOpState.Success("«${draft.name}» adicionado.")
                }
                .onFailure {
                    _state.value = RepoOpState.Error(it.message ?: "Falha ao adicionar jogo.")
                }
        }
    }

    fun startAdd() { _addingForm.value = true }

    fun cancelAdd() { _addingForm.value = false }

    fun deleteGame(slug: String, name: String) {
        _state.value = RepoOpState.Loading
        viewModelScope.launch {
            localRepoRepository.removeLocalGame(localRepoRepository.defaultDir, slug)
                .onSuccess {
                    repoManager.syncFromLocal()
                    _state.value = RepoOpState.Success("«$name» removido do catálogo.")
                }
                .onFailure {
                    _state.value = RepoOpState.Error(it.message ?: "Falha ao remover jogo.")
                }
        }
    }

    fun cancelEdit() {
        _editingSlug.value = null
        _editingDraft.value = null
    }

    // --- PUBLISH ---
    fun onPublishUrlChanged(v: String) { _publishUrl.value = v }
    fun onPublishTokenChanged(v: String) { _publishToken.value = v }

    fun publishToGithub() {
        val url = _publishUrl.value.trim()
        val token = _publishToken.value.trim()
        if (url.isEmpty() || token.isEmpty()) {
            _state.value = RepoOpState.Error("Informe a URL do repositório e o token de acesso.")
            return
        }
        val config = parseRepoUrl(url)
        if (config == null) {
            _state.value = RepoOpState.Error("URL inválida. Use https://github.com/usuario/repo")
            return
        }
        _state.value = RepoOpState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            localRepoRepository.publishToGithub(localRepoRepository.defaultDir, config, token)
                .onSuccess { files ->
                    _state.value = RepoOpState.Success(
                        "Publicado com sucesso (${files.size} arquivo(s)) em github.com/${config.owner}/${config.repo}."
                    )
                }
                .onFailure {
                    _state.value = RepoOpState.Error(it.message ?: "Falha ao publicar no GitHub.")
                }
        }
    }

    fun dismissState() { _state.value = RepoOpState.Idle }

    companion object {
        fun parseRepoUrl(url: String): RepoConfig? {
            if (url.contains("?") || url.contains("#")) return null
            val parsed = com.recomp.gameshub.core.util.GitHubUrls.parseRepoUrl(url) ?: return null
            return RepoConfig(
                sourceType = RepoSourceType.GITHUB_URL,
                owner = parsed.owner,
                repo = parsed.repo,
                branch = parsed.branch ?: com.recomp.gameshub.BuildConfig.DATA_BRANCH,
            )
        }
    }
}