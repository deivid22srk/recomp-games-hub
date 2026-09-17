package com.recomp.gameshub.presentation.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomp.gameshub.core.util.CoverResolver
import com.recomp.gameshub.data.repository.CatalogRepository
import com.recomp.gameshub.data.repository.DownloadRepository
import com.recomp.gameshub.domain.model.DownloadPhase
import com.recomp.gameshub.domain.model.DownloadTask
import com.recomp.gameshub.download.DownloadService
import com.recomp.gameshub.download.InstallHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

data class DownloadsUiState(
    val active: List<DownloadTask> = emptyList(),
    val finished: List<DownloadTask> = emptyList(),
)

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
    catalogRepository: CatalogRepository,
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

    /** slug → URL da capa, para exibir a capa real do jogo na lista de downloads. */
    val covers: StateFlow<Map<String, String>> = catalogRepository.observeSummaries()
        .map { list ->
            list.mapNotNull { summary ->
                CoverResolver.resolve(summary.slug, summary.cover)
                    ?.let { url -> summary.slug to url }
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
        // O mapeamento slug→pacote é gravado pelo InstallStatusReceiver somente
        // quando o instalador do sistema confirma sucesso (STATUS_SUCCESS).
        if (InstallHelper.canRequestInstalls(context)) {
            InstallHelper.installPackage(context, file, task.id)
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
