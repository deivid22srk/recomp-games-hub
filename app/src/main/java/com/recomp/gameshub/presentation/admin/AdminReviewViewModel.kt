package com.recomp.gameshub.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomp.gameshub.data.repository.AuthRepository
import com.recomp.gameshub.data.repository.ContributionRepository
import com.recomp.gameshub.domain.model.GameSubmission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminReviewUiState(
    val pending: List<GameSubmission> = emptyList(),
    val all: List<GameSubmission> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null,
    val isAdmin: Boolean = false,
)

class AdminReviewViewModel(
    private val contributionRepository: ContributionRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private data class AdminLists(
        val pending: List<GameSubmission>,
        val all: List<GameSubmission>,
    )

    private val _pending = MutableStateFlow<List<GameSubmission>>(emptyList())
    private val _all = MutableStateFlow<List<GameSubmission>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)
    private val _isAdmin = MutableStateFlow(authRepository.isAdmin.value)

    val uiState: StateFlow<AdminReviewUiState> = combine(
        combine(_pending, _all) { pending, all -> AdminLists(pending, all) },
        combine(_isLoading, _error, _successMessage, _isAdmin) { loading, error, success, admin ->
            AdminReviewUiState(isLoading = loading, error = error, successMessage = success, isAdmin = admin)
        },
    ) { lists, status ->
        AdminReviewUiState(
            pending = lists.pending,
            all = lists.all,
            isLoading = status.isLoading,
            error = status.error,
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
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            contributionRepository.pendingSubmissions()
                .onSuccess { _pending.value = it }
                .onFailure {
                    _error.value = it.message
                    _pending.value = emptyList()
                }
            _isLoading.value = false
            contributionRepository.allSubmissions()
                .onSuccess { _all.value = it }
        }
    }

    fun approve(slug: String) {
        viewModelScope.launch {
            contributionRepository.approve(slug)
                .onSuccess {
                    _successMessage.value = "Aprovado: $slug"
                    _pending.value = _pending.value.filterNot { it.slug == slug }
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun reject(slug: String, reason: String) {
        viewModelScope.launch {
            contributionRepository.reject(slug, reason)
                .onSuccess {
                    _successMessage.value = "Rejeitado: $slug"
                    _pending.value = _pending.value.filterNot { it.slug == slug }
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun update(game: GameSubmission) {
        viewModelScope.launch {
            contributionRepository.adminUpdate(game.slug, game)
                .onSuccess {
                    _successMessage.value = "Contribuição atualizada."
                    refresh()
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun delete(slug: String) {
        viewModelScope.launch {
            contributionRepository.adminDelete(slug)
                .onSuccess {
                    _successMessage.value = "Contribuição excluída."
                    refresh()
                }
                .onFailure { _error.value = it.message }
        }
    }
}
