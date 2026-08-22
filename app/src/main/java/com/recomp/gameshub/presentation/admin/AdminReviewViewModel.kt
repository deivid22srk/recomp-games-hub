package com.recomp.gameshub.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomp.gameshub.data.repository.AuthRepository
import com.recomp.gameshub.data.repository.ContributionRepository
import com.recomp.gameshub.domain.model.AppUpdateInfo
import com.recomp.gameshub.domain.model.GameSubmission
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AdminPromotionUiState {
    data object Idle : AdminPromotionUiState
    data object Loading : AdminPromotionUiState
    data class Result(val ok: Boolean, val message: String) : AdminPromotionUiState
}

enum class PrincipalAdminStatus {
    Unknown,
    Loading,
    Granted,
    Denied,
    Failed,
}

data class AdminActionState(
    val inProgress: Boolean = false,
    val error: String? = null,
)

sealed interface AppReleaseUiState {
    data object Idle : AppReleaseUiState
    data object Loading : AppReleaseUiState
    data class Result(val ok: Boolean, val message: String) : AppReleaseUiState
}

data class AdminAppUpdateState(
    val isLoading: Boolean = false,
    val current: AppUpdateInfo? = null,
    val loadError: String? = null,
)

data class AdminReviewUiState(
    val pending: List<GameSubmission> = emptyList(),
    val all: List<GameSubmission> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val allError: String? = null,
    val successMessage: String? = null,
    val isAdmin: Boolean = false,
)

class AdminReviewViewModel(
    private val contributionRepository: ContributionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private data class AdminLists(
        val pending: List<GameSubmission>,
        val all: List<GameSubmission>,
    )

    private var refreshJob: Job? = null
    private var principalJob: Job? = null

    private val _pending = MutableStateFlow<List<GameSubmission>>(emptyList())
    private val _all = MutableStateFlow<List<GameSubmission>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _allError = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)
    private val _isAdmin = MutableStateFlow(authRepository.isAdmin.value)
    private val _principalStatus = MutableStateFlow(PrincipalAdminStatus.Unknown)
    private val _promotionState = MutableStateFlow<AdminPromotionUiState>(AdminPromotionUiState.Idle)
    private val _actionStates = MutableStateFlow<Map<String, AdminActionState>>(emptyMap())
    private val _appUpdate = MutableStateFlow(AdminAppUpdateState())
    private val _appReleaseAction = MutableStateFlow<AppReleaseUiState>(AppReleaseUiState.Idle)

    val principalStatus: StateFlow<PrincipalAdminStatus> = _principalStatus.asStateFlow()
    val promotionState: StateFlow<AdminPromotionUiState> = _promotionState.asStateFlow()
    val actionStates: StateFlow<Map<String, AdminActionState>> = _actionStates.asStateFlow()
    val appUpdate: StateFlow<AdminAppUpdateState> = _appUpdate.asStateFlow()
    val appReleaseAction: StateFlow<AppReleaseUiState> = _appReleaseAction.asStateFlow()

    val uiState: StateFlow<AdminReviewUiState> = combine(
        combine(_pending, _all) { pending, all -> AdminLists(pending, all) },
        combine(_isLoading, _error, _allError, _successMessage, _isAdmin) { loading, error, allError, success, admin ->
            AdminReviewUiState(isLoading = loading, error = error, allError = allError, successMessage = success, isAdmin = admin)
        },
    ) { lists, status ->
        AdminReviewUiState(
            pending = lists.pending,
            all = lists.all,
            isLoading = status.isLoading,
            error = status.error,
            allError = status.allError,
            successMessage = status.successMessage,
            isAdmin = status.isAdmin,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminReviewUiState())

    init {
        viewModelScope.launch {
            authRepository.isAdmin.collect { admin ->
                _isAdmin.value = admin
                if (admin) refresh()
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            fetchPrincipalStatus()
            _isLoading.value = true
            _error.value = null
            _allError.value = null
            contributionRepository.pendingSubmissions()
                .onSuccess { _pending.value = it }
                .onFailure {
                    _error.value = it.message
                    _pending.value = emptyList()
                }
            _isLoading.value = false
            contributionRepository.allSubmissions()
                .onSuccess {
                    _all.value = it
                    _allError.value = null
                }
                .onFailure {
                    _allError.value = it.message
                }
        }
    }

    /**
     * The principal admin is defined exclusively on the backend, so the app
     * asks the server whether the signed-in user is allowed to promote.
     */
    fun refreshPrincipalStatus() {
        principalJob?.cancel()
        principalJob = viewModelScope.launch {
            fetchPrincipalStatus()
        }
    }

    private suspend fun fetchPrincipalStatus() {
        _principalStatus.value = PrincipalAdminStatus.Loading
        contributionRepository.isPrincipalAdmin()
            .onSuccess { granted ->
                _principalStatus.value = if (granted) {
                    PrincipalAdminStatus.Granted
                } else {
                    PrincipalAdminStatus.Denied
                }
            }
            .onFailure {
                _principalStatus.value = PrincipalAdminStatus.Failed
            }
    }

    fun approve(slug: String, name: String) {
        val key = "approve:$slug"
        if (_actionStates.value[key]?.inProgress == true) return
        beginAction(key)
        viewModelScope.launch {
            contributionRepository.approve(slug)
                .onSuccess {
                    removeAction(key)
                    _successMessage.value = "«$name» aprovada e publicada."
                    _pending.value = _pending.value.filterNot { it.slug == slug }
                    _all.value = _all.value.map { sub ->
                        if (sub.slug == slug) sub.copy(status = STATUS_APPROVED, reviewReason = null) else sub
                    }
                }
                .onFailure {
                    failAction(key, it.message ?: "Não foi possível aprovar a contribuição.")
                }
        }
    }

    fun reject(slug: String, name: String, reason: String) {
        val key = "reject:$slug"
        if (_actionStates.value[key]?.inProgress == true) return
        beginAction(key)
        viewModelScope.launch {
            contributionRepository.reject(slug, reason)
                .onSuccess {
                    succeedAction(key)
                    _successMessage.value = "«$name» rejeitada."
                    _pending.value = _pending.value.filterNot { it.slug == slug }
                    _all.value = _all.value.map { sub ->
                        if (sub.slug == slug) {
                            sub.copy(
                                status = STATUS_REJECTED,
                                reviewReason = reason.trim().takeIf { it.isNotBlank() },
                            )
                        } else {
                            sub
                        }
                    }
                }
                .onFailure {
                    failAction(key, it.message ?: "Não foi possível rejeitar a contribuição.")
                }
        }
    }

    fun update(originalSlug: String, game: GameSubmission) {
        val key = "update:$originalSlug"
        if (_actionStates.value[key]?.inProgress == true) return
        beginAction(key)
        viewModelScope.launch {
            contributionRepository.adminUpdate(game.slug, game)
                .onSuccess {
                    succeedAction(key)
                    _successMessage.value = "Contribuição atualizada."
                    refresh()
                }
                .onFailure {
                    failAction(key, it.message ?: "Não foi possível atualizar a contribuição.")
                }
        }
    }

    fun delete(slug: String) {
        val key = "delete:$slug"
        if (_actionStates.value[key]?.inProgress == true) return
        beginAction(key)
        viewModelScope.launch {
            contributionRepository.adminDelete(slug)
                .onSuccess {
                    succeedAction(key)
                    _successMessage.value = "Contribuição excluída."
                    refresh()
                }
                .onFailure {
                    failAction(key, it.message ?: "Não foi possível excluir a contribuição.")
                }
        }
    }

    fun clearAction(key: String) {
        _actionStates.value = _actionStates.value - key
    }

    private fun removeAction(key: String) {
        _actionStates.value = _actionStates.value - key
    }

    private fun beginAction(key: String) {
        _actionStates.value = _actionStates.value + (key to AdminActionState(inProgress = true))
    }

    private fun succeedAction(key: String) {
        _actionStates.value = _actionStates.value + (key to AdminActionState(inProgress = false))
    }

    private fun failAction(key: String, message: String?) {
        _actionStates.value = _actionStates.value + (key to AdminActionState(error = message))
    }

    fun promote(email: String) {
        if (_promotionState.value is AdminPromotionUiState.Loading) return
        _promotionState.value = AdminPromotionUiState.Loading
        viewModelScope.launch {
            contributionRepository.promoteAdmin(email)
                .onSuccess { result ->
                    _promotionState.value = AdminPromotionUiState.Result(
                        ok = result.ok,
                        message = result.message,
                    )
                }
                .onFailure { throwable ->
                    _promotionState.value = AdminPromotionUiState.Result(
                        ok = false,
                        message = throwable.message ?: "Não foi possível promover o usuário.",
                    )
                }
        }
    }

    fun dismissPromotion() {
        _promotionState.value = AdminPromotionUiState.Idle
    }

    // ---------- App update publishing (principal admin) ----------

    fun refreshAppRelease() {
        if (_appUpdate.value.isLoading) return
        _appUpdate.value = _appUpdate.value.copy(isLoading = true, loadError = null)
        viewModelScope.launch {
            contributionRepository.latestAppRelease()
                .onSuccess { release ->
                    _appUpdate.value = AdminAppUpdateState(current = release)
                }
                .onFailure { failure ->
                    _appUpdate.value = AdminAppUpdateState(
                        loadError = failure.message ?: "Não foi possível carregar a atualização publicada.",
                    )
                }
        }
    }

    fun publishAppRelease(versionName: String, downloadUrl: String, notes: String?) {
        if (_appReleaseAction.value is AppReleaseUiState.Loading) return
        _appReleaseAction.value = AppReleaseUiState.Loading
        viewModelScope.launch {
            contributionRepository.publishAppRelease(versionName, downloadUrl, notes)
                .onSuccess {
                    _appReleaseAction.value = AppReleaseUiState.Result(
                        ok = true,
                        message = "Atualização v${versionName.trim()} publicada. Os usuários verão o aviso ao abrir o app.",
                    )
                    refreshAppRelease()
                }
                .onFailure { failure ->
                    _appReleaseAction.value = AppReleaseUiState.Result(
                        ok = false,
                        message = failure.message ?: "Não foi possível publicar a atualização.",
                    )
                }
        }
    }

    fun removeAppRelease(id: String) {
        if (_appReleaseAction.value is AppReleaseUiState.Loading) return
        _appReleaseAction.value = AppReleaseUiState.Loading
        viewModelScope.launch {
            contributionRepository.deleteAppRelease(id)
                .onSuccess {
                    _appReleaseAction.value = AppReleaseUiState.Result(
                        ok = true,
                        message = "Publicação removida. Nenhuma atualização será anunciada.",
                    )
                    refreshAppRelease()
                }
                .onFailure { failure ->
                    _appReleaseAction.value = AppReleaseUiState.Result(
                        ok = false,
                        message = failure.message ?: "Não foi possível remover a publicação.",
                    )
                }
        }
    }

    fun dismissAppReleaseResult() {
        _appReleaseAction.value = AppReleaseUiState.Idle
    }

    fun dismissMessages() {
        _successMessage.value = null
        _error.value = null
    }

    fun dismissAllError() {
        _allError.value = null
    }

    companion object {
        private const val STATUS_APPROVED = "approved"
        private const val STATUS_REJECTED = "rejected"
    }
}