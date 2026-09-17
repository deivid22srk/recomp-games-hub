package com.recomp.gameshub.presentation.catalog

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.EmptyStateBox
import com.recomp.gameshub.core.designsystem.ErrorStateBox
import com.recomp.gameshub.core.designsystem.GameCoverImage
import com.recomp.gameshub.core.designsystem.InfoBanner
import com.recomp.gameshub.core.designsystem.RecompStatusBadge
import com.recomp.gameshub.core.designsystem.ShimmerBox
import com.recomp.gameshub.core.navigation.Routes
import com.recomp.gameshub.core.navigation.RecompBottomBar
import com.recomp.gameshub.core.designsystem.AppTopBar
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.core.util.CoverResolver
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
    // rememberSaveable mantém a busca aberta ao girar a tela.
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    var displayedSlug by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedSlug) {
        if (selectedSlug != null) {
            displayedSlug = selectedSlug
        } else if (displayedSlug != null) {
            // Aguarda a animação de saída antes de remover o conteúdo da memória.
            delay(300)
            if (selectedSlug == null) displayedSlug = null
        }
    }
    val isShowingDetails = selectedSlug != null

    Box(modifier = Modifier.fillMaxSize()) {
        if (searchOpen) {
            GameSearchScreen(
                viewModel = viewModel,
                onClose = {
                    viewModel.clearSearch()
                    searchOpen = false
                },
                onOpenGame = viewModel::selectGame,
            )
        } else Scaffold(
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
                    onFilterChange = viewModel::setFilter,
                    onOpenGame = viewModel::selectGame,
                    onOpenSearch = { searchOpen = true },
                    onDismissError = viewModel::dismissError,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            if (isShowingDetails || displayedSlug != null) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isShowingDetails,
                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320)),
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)),
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
    onFilterChange: (GameStatus?) -> Unit,
    onOpenGame: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pull-to-refresh disponível em toda a tela, inclusive nos estados
    // vazio/erro — antes só o grid permitia atualizar.
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CatalogHeader(onSearch = onOpenSearch)

            Spacer(Modifier.height(12.dp))

            FilterChipsRow(
                selected = state.filter,
                counts = state.counts,
                onSelect = onFilterChange,
            )

            Spacer(Modifier.height(8.dp))

            // Falha de refresh com lista já carregada: banner discreto e
            // dispensável, sem esconder o catálogo.
            if (state.error != null && state.games.isNotEmpty()) {
                InfoBanner(
                    message = state.error,
                    isError = true,
                    leadingIcon = null,
                    onDismiss = onDismissError,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

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
                    GameGrid(
                        games = state.games,
                        onOpenGame = onOpenGame,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GameGrid(
    games: List<GameSummary>,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 28.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(games, key = { it.slug }) { game ->
            GameCardItem(
                game = game,
                onClick = { onOpenGame(game.slug) },
            )
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
            .clickable(onClick = onClick, role = Role.Button),
    ) {
        GameCoverImage(
            url = CoverResolver.resolve(game.slug, game.cover),
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
private fun CatalogHeader(onSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
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
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Pesquisar jogos por nome",
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            )
            Text(
                text = "Abrir",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameSearchScreen(
    viewModel: CatalogViewModel,
    onClose: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val error by viewModel.searchError.collectAsStateWithLifecycle()
    var input by rememberSaveable(query) { mutableStateOf(query) }

    Scaffold(
        topBar = { AppTopBar(title = "Pesquisar jogos", onBack = onClose) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nome do jogo") },
                    placeholder = { Text("Ex.: Sonic, Mario, Zelda…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { viewModel.searchGames(input) },
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = { viewModel.searchGames(input) },
                            enabled = input.trim().length >= 2,
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = "Buscar")
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                )
            }
            Text(
                text = "Digite pelo menos 2 caracteres para pesquisar no catálogo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            when {
                query.isBlank() && results.isEmpty() && error == null -> Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Encontre um jogo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Pesquise pelo nome da recompilação e encontre rapidamente o que deseja baixar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorStateBox(message = error!!, onRetry = { viewModel.searchGames(input) })
                }
                query.trim().length >= 2 && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateBox(title = "Nenhum jogo encontrado", message = "Tente pesquisar por outro nome.")
                }
                results.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(results, key = { it.slug }) { game ->
                        GameCardItem(game = game, onClick = { onOpenGame(game.slug) })
                    }
                }
            }
        }
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
                    Text(if (status == null && count > 0) "$label ($count)" else label)
                },
            )
        }
    }
}
