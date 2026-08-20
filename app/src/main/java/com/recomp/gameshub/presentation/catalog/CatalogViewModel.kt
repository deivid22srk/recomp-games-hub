package com.recomp.gameshub.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomp.gameshub.data.repository.CatalogRepository
import com.recomp.gameshub.domain.model.GameStatus
import com.recomp.gameshub.domain.model.GameSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogUiState(
    val games: List<GameSummary> = emptyList(),
    val query: String = "",
    val filter: GameStatus? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val counts: Map<GameStatus, Int> = emptyMap(),
)

class CatalogViewModel(
    private val repository: CatalogRepository,
) : ViewModel() {

    private val summaries: StateFlow<List<GameSummary>> = repository.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _filter = MutableStateFlow<GameStatus?>(null)
    val filter = _filter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _hasLoadedOnce = MutableStateFlow(false)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _searchResults = MutableStateFlow<List<GameSummary>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()

    private val _selectedSlug = MutableStateFlow<String?>(null)
    val selectedSlug = _selectedSlug.asStateFlow()

    val uiState: StateFlow<CatalogUiState> = combine(
        summaries,
        _query,
        _filter,
        _isRefreshing,
        _error,
    ) { list, q, filter, refreshing, error ->
        val catalog = list.take(30)
        val normalized = q.trim().lowercase()
        val filtered = catalog.filter { game ->
            (filter == null || game.status == filter) &&
                (normalized.isEmpty() || game.name.lowercase().contains(normalized))
        }
        CatalogUiState(
            games = filtered,
            query = q,
            filter = filter,
            isLoading = !_hasLoadedOnce.value && list.isEmpty() && error == null,
            isRefreshing = refreshing,
            error = error,
            counts = catalog.groupingBy { it.status }.eachCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.refresh()
            _isRefreshing.value = false
            if (result.isSuccess) {
                _hasLoadedOnce.value = true
                _error.value = null
            } else {
                if (initial && summaries.value.isEmpty()) {
                    _error.value = result.exceptionOrNull()?.message ?: "Falha ao carregar o catálogo"
                }
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFilter(status: GameStatus?) {
        _filter.value = status
    }

    fun searchGames(query: String) {
        _searchQuery.value = query
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            _searchError.value = null
            _isSearching.value = false
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            repository.search(query)
                .onSuccess { _searchResults.value = it }
                .onFailure {
                    _searchResults.value = emptyList()
                    _searchError.value = it.message ?: "Falha ao pesquisar jogos"
                }
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchError.value = null
        _isSearching.value = false
    }

    fun selectGame(slug: String) {
        _selectedSlug.value = slug
    }

    fun clearSelection() {
        _selectedSlug.value = null
    }
}
