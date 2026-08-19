package com.recomp.gameshub.presentation.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.CoverFallback
import com.recomp.gameshub.core.designsystem.EmptyStateBox
import com.recomp.gameshub.core.navigation.Routes
import com.recomp.gameshub.core.navigation.RecompBottomBar
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.core.util.formatBytes
import com.recomp.gameshub.core.util.formatEtaSimple
import com.recomp.gameshub.core.util.formatSpeed
import com.recomp.gameshub.core.util.percentage
import com.recomp.gameshub.domain.model.DownloadPhase
import com.recomp.gameshub.domain.model.DownloadTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsRoute(
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: DownloadsViewModel = appViewModel {
        DownloadsViewModel(it.downloadRepository, it.appContext)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SmallTopAppBar(
                title = {
                    Text("Downloads", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (uiState.finished.isNotEmpty() || uiState.active.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearFinished) {
                            Text("Limpar concluídos")
                        }
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            RecompBottomBar(
                currentRoute = Routes.Downloads,
                onNavigate = { route ->
                    when (route) {
                        Routes.Catalog -> onOpenCatalog()
                        Routes.Settings -> onOpenSettings()
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.active.isEmpty() && uiState.finished.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateBox(
                    title = "Nenhum download ainda",
                    message = "Escolha um jogo no catálogo e toque em Baixar. Acompanhe o progresso aqui e na notificação.",
                    icon = Icons.Rounded.Schedule,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.active.isNotEmpty()) {
                item {
                    SectionLabel("Em andamento", Modifier.padding(bottom = 4.dp))
                }
                items(uiState.active, key = { it.id }) { task ->
                    ActiveDownloadItem(
                        task = task,
                        viewModel = viewModel,
                    )
                }
            }

            if (uiState.active.isNotEmpty() && uiState.finished.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            if (uiState.finished.isNotEmpty()) {
                item {
                    SectionLabel("Concluídos", Modifier.padding(bottom = 4.dp))
                }
                items(uiState.finished, key = { it.id }) { task ->
                    FinishedDownloadItem(task = task, viewModel = viewModel)
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ActiveDownloadItem(
    task: DownloadTask,
    viewModel: DownloadsViewModel,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            CoverFallback(
                key = task.id.hashCode().toLong(),
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.gameName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val paused = task.phase == DownloadPhase.PAUSED
                Text(
                    text = statusLine(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (paused) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (task.totalBytes > 0) task.progress else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (paused) {
                    PauseResumeButton(paused = true) { viewModel.resume(task.id) }
                } else {
                    PauseResumeButton(paused = false) { viewModel.pause(task.id) }
                }
                FilledTonalIconButton(
                    onClick = { viewModel.cancel(task.id) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancelar")
                }
            }
        }
    }
}

@Composable
private fun PauseResumeButton(paused: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            contentDescription = if (paused) "Retomar" else "Pausar",
        )
    }
}

@Composable
private fun statusLine(task: DownloadTask): String {
    if (task.totalBytes > 0L) {
        val percent = percentage(task)
        return when (task.phase) {
            DownloadPhase.DOWNLOADING, DownloadPhase.PENDING -> {
                val parts = mutableListOf("$percent%")
                parts += "${formatBytes(task.downloadedBytes)} de ${formatBytes(task.totalBytes)}"
                val speed = formatSpeed(task.speedBytesPerSec)
                if (speed != "—") parts += speed
                formatEtaSimple(task)?.let { parts += it }
                parts.joinToString("  •  ")
            }
            DownloadPhase.PAUSED -> "Pausado  •  ${formatBytes(task.downloadedBytes)} de ${formatBytes(task.totalBytes)}"
            else -> "${formatBytes(task.downloadedBytes)} de ${formatBytes(task.totalBytes)}"
        }
    }
    return when (task.phase) {
        DownloadPhase.PAUSED -> "Pausado"
        DownloadPhase.DOWNLOADING, DownloadPhase.PENDING -> "Baixando…"
        else -> ""
    }
}

@Composable
private fun FinishedDownloadItem(
    task: DownloadTask,
    viewModel: DownloadsViewModel,
) {
    val completed = task.phase == DownloadPhase.COMPLETED
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverFallback(
                key = task.id.hashCode().toLong(),
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.gameName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (completed) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (completed) {
                            "Concluído  •  ${formatBytes(task.totalBytes)}"
                        } else {
                            task.errorMessage ?: "Falha no download"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (completed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (completed) {
                    Button(
                        onClick = { viewModel.install(task) },
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        Text("Instalar")
                    }
                    IconButton(onClick = { viewModel.delete(task.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Excluir")
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.retry(task.id) },
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tentar novamente")
                    }
                    IconButton(onClick = { viewModel.delete(task.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Excluir")
                    }
                }
            }
        }
    }
}