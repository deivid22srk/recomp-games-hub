package com.recomp.gameshub.presentation.contribution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomp.gameshub.data.repository.AuthRepository
import com.recomp.gameshub.data.repository.ContributionRepository
import com.recomp.gameshub.data.remote.supabase.toSlugOrNull
import com.recomp.gameshub.domain.model.AuthState
import com.recomp.gameshub.domain.model.GameSubmission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContributionViewModel(
    private val contributionRepository: ContributionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(authRepository.state.value)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isAdmin = MutableStateFlow(authRepository.isAdmin.value)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _submissions = MutableStateFlow<List<GameSubmission>>(emptyList())
    val submissions: StateFlow<List<GameSubmission>> = _submissions.asStateFlow()

    private val _isLoadingSubmissions = MutableStateFlow(false)
    val isLoadingSubmissions: StateFlow<Boolean> = _isLoadingSubmissions.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _submitError = MutableStateFlow<String?>(null)
    val submitError: StateFlow<String?> = _submitError.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _formVisible = MutableStateFlow(false)
    val formVisible: StateFlow<Boolean> = _formVisible.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.state.collect { auth ->
                _authState.value = auth
                if (auth is AuthState.SignedIn) {
                    loadSubmissions()
                    authRepository.refreshAdminProfile()
                    _isAdmin.value = authRepository.isAdmin.value
                } else {
                    _submissions.value = emptyList()
                    _isAdmin.value = false
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (_isAuthenticating.value) return
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            authRepository.signIn(email, password)
                .onSuccess { _successMessage.value = "Login realizado." }
                .onFailure { _authError.value = it.message }
            _isAuthenticating.value = false
        }
    }

    fun signUp(email: String, password: String) {
        if (_isAuthenticating.value) return
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            authRepository.signUp(email, password)
                .onSuccess {
                    _successMessage.value =
                        if ((it.email ?: "").isBlank()) {
                            "Conta criada. Confira seu e-mail para confirmar o cadastro."
                        } else {
                            "Conta criada. Em alguns casos é preciso confirmar o e-mail antes de publicar."
                        }
                }
                .onFailure { _authError.value = it.message }
            _isAuthenticating.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _submissions.value = emptyList()
        }
    }

    fun resetPassword(email: String) {
        if (_isAuthenticating.value) return
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            authRepository.sendPasswordReset(email)
                .onSuccess { _successMessage.value = "Se enviado, o link de redefinição foi enviado para o seu e-mail." }
                .onFailure { _authError.value = it.message }
            _isAuthenticating.value = false
        }
    }

    fun toggleForm(visible: Boolean) {
        _formVisible.value = visible
        if (!visible) {
            _submitError.value = null
            _successMessage.value = null
        }
    }

    fun submitForm(
        name: String,
        status: String,
        version: String?,
        description: String,
        originalPlatform: String?,
        author: String?,
        sourceRepo: String?,
        apkUrl: String?,
        fileSizeBytes: Long,
        tags: List<String>,
        coverUrl: String?,
        bannerUrl: String?,
    ) {
        if (_isSubmitting.value) return
        val slug = name.toSlugOrNull()
        if (slug == null) {
            _submitError.value = "O nome do jogo é obrigatório."
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            _submitError.value = null
            val submission = GameSubmission(
                submissionId = "",
                slug = slug,
                name = name.trim(),
                status = REVIEW_PENDING_RESULT,
                devStatus = status.toDevStatus(),
                version = version?.trim()?.takeIf { it.isNotBlank() },
                description = description.trim(),
                originalPlatform = originalPlatform?.trim()?.takeIf { it.isNotBlank() },
                author = author?.trim()?.takeIf { it.isNotBlank() },
                sourceRepo = sourceRepo?.trim()?.takeIf { it.isNotBlank() },
                apkUrl = apkUrl?.trim()?.takeIf { it.isNotBlank() },
                fileSizeBytes = fileSizeBytes,
                tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                coverUrl = coverUrl?.trim()?.takeIf { it.isNotBlank() },
                bannerUrl = bannerUrl?.trim()?.takeIf { it.isNotBlank() },
            )
            contributionRepository.submit(submission)
                .onSuccess {
                    _formVisible.value = false
                    _successMessage.value = "Contribuição enviada para revisão."
                    loadSubmissions()
                }
                .onFailure { _submitError.value = it.message }
            _isSubmitting.value = false
        }
    }

    private fun loadSubmissions() {
        viewModelScope.launch {
            _isLoadingSubmissions.value = true
            contributionRepository.mySubmissions()
                .onSuccess { _submissions.value = it }
                .onFailure { /* silent: keep showing what we have */ }
            _isLoadingSubmissions.value = false
        }
    }

    private fun String.toDevStatus(): String =
        when (trim().lowercase()) {
            "released", "stable", "available", "complete" -> "released"
            "beta" -> "beta"
            "alpha" -> "alpha"
            "in_development", "in-development", "wip", "development" -> "in_development"
            else -> "in_development"
        }

    companion object {
        private const val REVIEW_PENDING_RESULT = "pending"
    }
}