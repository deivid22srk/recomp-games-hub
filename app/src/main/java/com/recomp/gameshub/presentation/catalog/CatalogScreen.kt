package com.recomp.gameshub.presentation.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.EmptyStateBox
import com.recomp.gameshub.core.designsystem.ErrorStateBox
import com.recomp.gameshub.core.designsystem.GameCoverImage
import com.recomp.gameshub.core.designsystem.RecompStatusBadge
import com.recomp.gameshub.core.designsystem.ShimmerBox
import com.recomp.gameshub.core.navigation.Routes
import com.recomp.gameshub.core.navigation.RecompBottomBar
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.domain.model.GameStatus
import com.recomp.gameshub.domain.model.GameSummary
import com.recomp.gameshub.presentation.details.GameDetailsScreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogRoute(
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: CatalogViewModel = appViewModel { it.catalogRepository.let { repo ->
        CatalogViewModel(repo)
    } }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedSlug by viewModel.selectedSlug.collectAsStateWithLifecycle()

    var displayedSlug by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedSlug) {
        if (selectedSlug != null) displayedSlug = selectedSlug
    }
    LaunchedEffect(selectedSlug) {
        if (selectedSlug == null && displayedSlug != null) {
            delay(280)
            if (selectedSlug == null) displayedSlug = null
        }
    }
    val isShowingDetails = selectedSlug != null

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                    RecompBottomBar(
                        currentRoute = Routes.Catalog,
                        onNavigate = { route ->
                            when (route) {
                                Routes.Downloads -> onOpenDownloads()
                                Routes.Settings -> onOpenSettings()
                            }
                        },
                    )
                },
            ) { innerPadding ->
                CatalogContent(
                    state = uiState,
                    onRefresh = { viewModel.refresh() },
                    onQueryChange = viewModel::setQuery,
                    onFilterChange = viewModel::setFilter,
                    onOpenGame = viewModel::selectGame,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            if (isShowingDetails || displayedSlug != null) {
                AnimatedVisibility(
                    visible = isShowingDetails,
                    enter = fadeIn(tween(320)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    displayedSlug?.let { slug ->
                        GameDetailsScreen(
                            slug = slug,
                            onClose = viewModel::clearSelection,
                        )
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogContent(
    state: CatalogUiState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (GameStatus?) -> Unit,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        CatalogHeader()

        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(12.dp))

        FilterChipsRow(
            selected = state.filter,
            counts = state.counts,
            onSelect = onFilterChange,
        )

        Spacer(Modifier.height(8.dp))

        when {
            state.isLoading -> GameGridSkeleton()
            state.games.isEmpty() && state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorStateBox(message = state.error, onRetry = onRefresh)
                }
            }
            state.games.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyStateBox(
                        title = if (state.query.isNotBlank() || state.filter != null) {
                            "Nenhum jogo encontrado"
                        } else {
                            "Catálogo vazio"
                        },
                        message = "Ajuste a busca/filtros ou puxe para atualizar o catálogo.",
                        actionLabel = "Atualizar",
                        onAction = onRefresh,
                    )
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 150.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 28.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                items(state.games, key = { it.slug }) { game ->
                                    GameCardItem(
                                            game = game,
                                            onClick = { onOpenGame(game.slug) },
                                        )
                                }
                                }
                            }
                        }
        }
    }
}

@Composable
private fun GameCardItem(
    game: GameSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coverModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(3f / 4f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        GameCoverImage(
            url = game.cover,
            contentDescription = game.name,
            modifier = coverModifier,
            shape = RoundedCornerShape(20.dp),
            fallbackKey = game.slug.hashCode().toLong(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = game.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RecompStatusBadge(status = game.status)
            game.originalPlatform?.let { platform ->
                Text(
                    text = platform,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CatalogHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Text(
            text = "Recomp Hub",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Recompilações. Um só lugar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GameGridSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        userScrollEnabled = false,
    ) {
        items(6) {
            Column {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f),
                    shape = RoundedCornerShape(20.dp),
                )
                Spacer(Modifier.height(10.dp))
                ShimmerBox(
                    modifier = Modifier.height(16.dp).fillMaxWidth(0.8f),
                    shape = RoundedCornerShape(4.dp),
                )
                Spacer(Modifier.height(8.dp))
                ShimmerBox(
                    modifier = Modifier.height(20.dp).fillMaxWidth(0.5f),
                    shape = RoundedCornerShape(50),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Buscar jogo…") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Limpar busca",
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun FilterChipsRow(
    selected: GameStatus?,
    counts: Map<GameStatus, Int>,
    onSelect: (GameStatus?) -> Unit,
) {
    val statuses = listOf(
        null to "Todos",
        GameStatus.RELEASED to "Disponíveis",
        GameStatus.BETA to "Beta",
        GameStatus.ALPHA to "Alpha",
        GameStatus.IN_DEVELOPMENT to "Em dev",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(statuses.size, key = { it }) { index ->
            val (status, label) = statuses[index]
            val count = if (status == null) counts.values.sum() else counts[status] ?: 0
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = {
                    Text("$label")
                },
                leadingIcon = {
                    if (status == null && count > 0) {
                        Text(
                            "$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}