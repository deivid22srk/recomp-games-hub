package com.recomp.gameshub.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.recomp.gameshub.core.util.formatBytes
import com.recomp.gameshub.core.util.percentage
import com.recomp.gameshub.domain.model.DownloadPhase
import com.recomp.gameshub.domain.model.DownloadTask
import com.recomp.gameshub.domain.model.GameStatus

@Composable
fun shimmerColor(): Color {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    return MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Box(modifier = modifier.background(shimmerColor(), shape))
}

private val CoverPalettes = listOf(
    listOf(Color(0xFF0B6E56), Color(0xFF3FA386)),
    listOf(Color(0xFF3E6374), Color(0xFF8FB7CD)),
    listOf(Color(0xFF6E4B8F), Color(0xFFB08BD0)),
    listOf(Color(0xFF8F5B2E), Color(0xFFD0A07B)),
    listOf(Color(0xFF8F3E55), Color(0xFFD07B97)),
    listOf(Color(0xFF2E6E8F), Color(0xFF7BB0D0)),
)

@Composable
fun CoverFallback(
    key: Long,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val palette = CoverPalettes[Math.floorMod(key, CoverPalettes.size.toLong()).toInt()]
    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(palette),
            shape = shape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Extension,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
fun GameCoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    fallbackKey: Long = url?.hashCode()?.toLong() ?: 0L,
) {
    if (url.isNullOrBlank()) {
        CoverFallback(key = fallbackKey, modifier = modifier, shape = shape)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape),
        loading = {
            ShimmerBox(modifier = Modifier.matchParentSize())
        },
        error = {
            CoverFallback(key = fallbackKey, modifier = Modifier.matchParentSize(), shape = shape)
        },
    )
}

@Composable
fun RecompStatusBadge(status: GameStatus) {
    val badgeColors = when (status) {
        GameStatus.RELEASED -> BadgeColors(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        GameStatus.BETA -> BadgeColors(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        GameStatus.ALPHA, GameStatus.IN_DEVELOPMENT -> BadgeColors(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        GameStatus.UNKNOWN -> BadgeColors(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        color = badgeColors.container,
        contentColor = badgeColors.content,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private data class BadgeColors(val container: Color, val content: Color)

@Composable
fun EmptyStateBox(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Extension,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(20.dp).size(48.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorStateBox(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyStateBox(
        title = "Algo deu errado",
        message = message,
        icon = Icons.Filled.CloudOff,
        modifier = modifier,
        actionLabel = "Tentar novamente",
        onAction = onRetry,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = 20.dp),
    )
}

@Composable
fun InfoBanner(message: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val onColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = color, shape = MaterialTheme.shapes.medium) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = onColor,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun DownloadControl(
    task: DownloadTask?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    downloadEnabled: Boolean = true,
) {
    val phase = task?.phase
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (phase) {
                    null, DownloadPhase.CANCELLED -> {
                        if (downloadEnabled) {
                            StartButton(task, onStart)
                        } else {
                            NoLinkButton()
                        }
                    }
                    DownloadPhase.PENDING, DownloadPhase.DOWNLOADING -> ProgressButton(task)
                    DownloadPhase.PAUSED -> ResumeButton(onResume)
                    DownloadPhase.COMPLETED -> InstallButton(task, onInstall)
                    DownloadPhase.FAILED -> RetryButton(task, onRetry)
                }
            }
            AnimatedVisibility(
                visible = phase == DownloadPhase.DOWNLOADING || phase == DownloadPhase.PENDING,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                PauseButton(phase == DownloadPhase.DOWNLOADING, onPause)
            }
            AnimatedVisibility(
                visible = phase == DownloadPhase.DOWNLOADING || phase == DownloadPhase.PENDING,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                CancelButton(onCancel)
            }
        }
        AnimatedVisibility(
            visible = phase == DownloadPhase.FAILED,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = task?.errorMessage ?: "Falha no download",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun NoLinkButton() {
    FilledTonalButton(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            "Sem link de download",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StartButton(task: DownloadTask?, onStart: () -> Unit) {
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Baixar", style = MaterialTheme.typography.titleMedium)
        if (task != null && task.totalBytes > 0L) {
            Spacer(Modifier.width(8.dp))
            Text(
                "• ${formatBytes(task.totalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ProgressButton(task: DownloadTask?) {
    val percent = percentage(task ?: return)
    FilledTonalButton(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            progress = { task.progress },
        )
        Spacer(Modifier.width(10.dp))
        val labelText = if (task.totalBytes > 0L) "$percent%" else "Baixando…"
        Text(labelText, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ResumeButton(onResume: () -> Unit) {
    Button(
        onClick = onResume,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Retomar", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RetryButton(task: DownloadTask?, onRetry: () -> Unit) {
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Tentar novamente", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun InstallButton(task: DownloadTask?, onInstall: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onInstall,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Instalar", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                "• pronto",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun PauseButton(active: Boolean, onPause: () -> Unit) {
    FilledTonalIconButton(
        onClick = onPause,
        modifier = Modifier.size(54.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(Icons.Filled.Pause, contentDescription = "Pausar")
    }
}

@Composable
private fun CancelButton(onCancel: () -> Unit) {
    FilledTonalIconButton(
        onClick = onCancel,
        modifier = Modifier.size(54.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(Icons.Filled.Close, contentDescription = "Cancelar")
    }
}

@Composable
fun DeleteDownloadsTextButton(onDelete: () -> Unit) {
    TextButton(onClick = onDelete) {
        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Excluir")
    }
}