package com.recomp.gameshub.presentation.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomp.gameshub.data.repository.CatalogRepository
import com.recomp.gameshub.data.remote.GithubRelease
import com.recomp.gameshub.data.remote.GithubReleaseApi
import com.recomp.gameshub.data.repository.DownloadRepository
import com.recomp.gameshub.domain.model.DownloadTask
import com.recomp.gameshub.domain.model.GameDetail
import com.recomp.gameshub.download.DownloadService
import com.recomp.gameshub.download.InstallHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class DetailsUiState(
    val detail: GameDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class DetailsViewModel(
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val context: Context,
    private val slug: String,
    private val githubReleaseApi: GithubReleaseApi,
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _releases = MutableStateFlow<List<GithubRelease>>(emptyList())
    val releases: StateFlow<List<GithubRelease>> = _releases.asStateFlow()
    private val _releaseError = MutableStateFlow<String?>(null)
    val releaseError: StateFlow<String?> = _releaseError.asStateFlow()

    val downloadTask: StateFlow<DownloadTask?> = downloadRepository.observeTask(slug)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<DetailsUiState> = combine(
        catalogRepository.observeDetail(slug),
        _loading,
        _error,
    ) { detail, loading, error ->
        DetailsUiState(
            detail = detail,
            isLoading = detail == null && error == null && loading,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsUiState())

    init {
        ensureDetail()
        viewModelScope.launch {
            val repository = catalogRepository.observeDetail(slug).filterNotNull().first().sourceRepo
            if (!repository.isNullOrBlank()) {
                runCatching { githubReleaseApi.fetchReleases(repository) }
                    .onSuccess { _releases.value = it }
                    .onFailure { _releaseError.value = it.message }
            }
        }
    }

    fun ensureDetail() {
        if (uiState.value.detail != null && uiState.value.error == null) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            catalogRepository.ensureDetail(slug)
            _loading.value = false
            val current = catalogRepository.observeDetail(slug).first()
            if (current == null) {
                _error.value = "Não foi possível carregar os detalhes deste jogo."
            }
        }
    }

    fun startDownload(url: String? = null) {
        uiState.value.detail?.let { detail ->
            downloadRepository.enqueue(if (url == null) detail else detail.copy(downloadUrl = url))
            DownloadService.start(context)
        }
    }

    fun pause() {
        downloadTask.value?.let { downloadRepository.pause(it.id) }
    }

    fun resume() {
        downloadTask.value?.let { task ->
            downloadRepository.resume(task.id)
            DownloadService.start(context)
        }
    }

    fun cancel() {
        downloadTask.value?.let { downloadRepository.cancel(it.id) }
    }

    fun retry() {
        downloadTask.value?.let { task ->
            downloadRepository.retry(task.id)
            DownloadService.start(context)
        }
    }

    fun install() {
        val task = downloadTask.value ?: return
        val file = File(task.localPath)
        if (!file.exists()) return
        if (InstallHelper.canRequestInstalls(context)) {
            InstallHelper.installPackage(context, file)
        } else {
            InstallHelper.openInstallPermissionSettings(context)
        }
    }

    fun share() {
        val detail = uiState.value.detail ?: return
        val text = buildString {
            append("🎮 ${detail.summary.name}")
            detail.summary.version?.let { append(" (v$it)") }
            append("\nRecomp para Android via Recomp Hub.")
            detail.downloadUrl?.let { append("\n\n$it") }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openSource() {
        val url = uiState.value.detail?.sourceRepo ?: uiState.value.detail?.downloadUrl ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
