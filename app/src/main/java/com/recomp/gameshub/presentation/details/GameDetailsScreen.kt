package com.recomp.gameshub.presentation.details

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.BuildConfig
import com.recomp.gameshub.core.designsystem.DownloadControl
import com.recomp.gameshub.core.designsystem.ErrorStateBox
import com.recomp.gameshub.core.designsystem.GameCoverImage
import com.recomp.gameshub.core.designsystem.RecompStatusBadge
import com.recomp.gameshub.core.designsystem.SectionHeader
import com.recomp.gameshub.core.designsystem.ShimmerBox
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.core.util.formatBytes
import com.recomp.gameshub.domain.model.GameDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsScreen(
    slug: String,
    onClose: () -> Unit,
) {
    val viewModel: DetailsViewModel = appViewModel(key = "detail-$slug") {
        DetailsViewModel(
            catalogRepository = it.catalogRepository,
            downloadRepository = it.downloadRepository,
            context = it.appContext,
            slug = slug,
        )
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadTask by viewModel.downloadTask.collectAsStateWithLifecycle()

    BackHandler(onBack = onClose)

    val hasDownloadUrl = uiState.detail?.downloadUrl?.isNotBlank() == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        when {
            uiState.error != null && uiState.detail == null -> {
                ErrorStateBox(
                    message = uiState.error ?: "Erro ao carregar os detalhes",
                    onRetry = viewModel::ensureDetail,
                    modifier = Modifier.align(Alignment.Center),
                )
                BackButton(onClose = onClose, modifier = Modifier.align(Alignment.TopStart))
            }
            uiState.detail == null -> {
                DetailsSkeleton()
                BackButton(onClose = onClose, modifier = Modifier.align(Alignment.TopStart))
            }
            else -> {
                val detail = uiState.detail!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 132.dp),
                ) {
                    HeroBanner(detail = detail, onClose = onClose, slug = slug)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp),
                    ) {
                        GameCoverImage(
                            url = resolved(detail, slug, detail.cover),
                            contentDescription = detail.summary.name,
                            modifier = Modifier
                                .offset(y = (-72).dp)
                                .width(118.dp)
                                .height(158.dp),
                            shape = RoundedCornerShape(18.dp),
                            fallbackKey = slug.hashCode().toLong(),
                        )
                    }
                    Spacer(Modifier.height(60.dp))
                    DetailContent(detail = detail, onShare = viewModel::share, onOpenSource = viewModel::openSource)
                }

                DetailBottomBar(
                    task = downloadTask,
                    onStart = viewModel::startDownload,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onCancel = viewModel::cancel,
                    onRetry = viewModel::retry,
                    onInstall = viewModel::install,
                    onDelete = { downloadTask?.let { viewModel.cancel() } },
                    downloadEnabled = hasDownloadUrl,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun resolved(detail: GameDetail, slug: String, path: String?): String? =
    when {
        path.isNullOrBlank() -> null
        path.startsWith("http://") || path.startsWith("https://") -> path
        else -> "https://raw.githubusercontent.com/" +
            "${BuildConfig.DATA_OWNER}/${BuildConfig.DATA_REPO}/${BuildConfig.DATA_BRANCH}" +
            "/games/$slug/$path"
    }

@Composable
private fun HeroBanner(
    detail: GameDetail,
    onClose: () -> Unit,
    slug: String,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        GameCoverImage(
            url = resolved(detail, slug, detail.banner),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            fallbackKey = (slug + "-banner").hashCode().toLong(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(
                onClose = onClose,
                modifier = Modifier.weight(1f),
                scrim = true,
            )
            detail.summary.version?.let { version ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("v$version", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scrim: Boolean = false,
) {
    val container = if (scrim) {
        Color.Black.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    }
    FilledIconButton(
        onClick = onClose,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = if (scrim) Color.White else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Voltar",
        )
    }
}

@Composable
private fun DetailContent(
    detail: GameDetail,
    onShare: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RecompStatusBadge(status = detail.summary.status)
            detail.summary.originalPlatform?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = detail.summary.name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        if (detail.description.isNotBlank()) {
            Text(
                text = detail.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (detail.tags.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                detail.tags.take(6).forEach { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tag,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(tag, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (detail.screenshots.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            SectionHeader("Capturas de tela")
            Spacer(Modifier.height(12.dp))
            ScreenshotsCarousel(detail = detail, slug = detail.summary.slug)
        }

        Spacer(Modifier.height(28.dp))
        SectionHeader("Informações")
        Spacer(Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                InfoRow(
                    icon = Icons.Rounded.Android,
                    label = "Plataforma original",
                    value = detail.summary.originalPlatform ?: "—",
                )
                InfoRow(
                    icon = Icons.Rounded.Person,
                    label = "Autor do recomp",
                    value = detail.author ?: "—",
                )
                InfoRow(
                    icon = Icons.Rounded.Storage,
                    label = "Tamanho",
                    value = if (detail.fileSizeBytes > 0) formatBytes(detail.fileSizeBytes) else "—",
                )
                if (detail.lastUpdated != null) {
                    InfoRow(
                        icon = Icons.Rounded.Info,
                        label = "Atualizado",
                        value = detail.lastUpdated,
                    )
                }
                if (detail.summary.version != null) {
                    InfoRow(
                        icon = Icons.Rounded.Verified,
                        label = "Versão",
                        value = detail.summary.version,
                    )
                }
                if (!detail.sourceRepo.isNullOrBlank()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    FilledTonalButton(
                        onClick = onOpenSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Código-fonte do recomp")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartilhar")
            }
            if (!detail.downloadUrl.isNullOrBlank()) {
                FilledTonalButton(
                    onClick = {
                        detail.downloadUrl?.let { url ->
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Link direto")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotsCarousel(
    detail: GameDetail,
    slug: String,
) {
    val pagerState = rememberPagerState(pageCount = { detail.screenshots.size })
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        pageSpacing = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) { page ->
        GameCoverImage(
            url = resolved(detail, slug, detail.screenshots[page]),
            contentDescription = "Captura ${page + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(20.dp),
            fallbackKey = (slug + page).hashCode().toLong(),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(detail.screenshots.size) { index ->
            val selected = pagerState.currentPage == index
            val size = if (selected) 8.dp else 6.dp
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(size)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun DetailsSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        )
        Column(modifier = Modifier.padding(20.dp)) {
            ShimmerBox(modifier = Modifier.height(24.dp).fillMaxWidth(0.5f))
            Spacer(Modifier.height(14.dp))
            ShimmerBox(modifier = Modifier.height(30.dp).fillMaxWidth(0.7f))
            Spacer(Modifier.height(16.dp))
            ShimmerBox(modifier = Modifier.height(14.dp).fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            ShimmerBox(modifier = Modifier.height(14.dp).fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            ShimmerBox(modifier = Modifier.height(14.dp).fillMaxWidth(0.6f))
            Spacer(Modifier.height(28.dp))
            ShimmerBox(modifier = Modifier.height(160.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun DetailBottomBar(
    task: com.recomp.gameshub.domain.model.DownloadTask?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    downloadEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
    ) {
        DownloadControl(
            task = task,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRetry = onRetry,
            onInstall = onInstall,
            onDelete = onDelete,
            downloadEnabled = downloadEnabled,
        )
    }
}