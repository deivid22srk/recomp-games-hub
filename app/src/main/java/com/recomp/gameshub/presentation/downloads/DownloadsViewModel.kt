package com.recomp.gameshub.presentation.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import com.recomp.gameshub.data.repository.DownloadRepository
import com.recomp.gameshub.domain.model.DownloadPhase
import com.recomp.gameshub.domain.model.DownloadTask
import com.recomp.gameshub.download.DownloadService
import com.recomp.gameshub.download.InstallHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import androidx.lifecycle.viewModelScope
import java.io.File

data class DownloadsUiState(
    val active: List<DownloadTask> = emptyList(),
    val finished: List<DownloadTask> = emptyList(),
)

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
    private val context: Context,
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> = downloadRepository.tasks
        .map { tasks ->
            DownloadsUiState(
                active = tasks.filter { it.isActive },
                finished = tasks.filter { !it.isActive },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun pause(id: String) = downloadRepository.pause(id)

    fun resume(id: String) {
        downloadRepository.resume(id)
        DownloadService.start(context)
    }

    fun cancel(id: String) = downloadRepository.cancel(id)

    fun retry(id: String) {
        downloadRepository.retry(id)
        DownloadService.start(context)
    }

    fun delete(id: String) = downloadRepository.delete(id)

    fun install(task: DownloadTask) {
        val file = File(task.localPath)
        if (!file.exists()) return
        if (InstallHelper.canRequestInstalls(context)) {
            InstallHelper.installPackage(context, file)
        } else {
            InstallHelper.openInstallPermissionSettings(context)
        }
    }

    fun clearFinished() {
        downloadRepository.tasks.value
            .filter { it.phase == DownloadPhase.COMPLETED || it.phase == DownloadPhase.FAILED }
            .forEach { downloadRepository.delete(it.id) }
    }
}