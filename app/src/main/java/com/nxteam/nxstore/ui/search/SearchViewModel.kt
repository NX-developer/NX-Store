package com.nxteam.nxstore.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxteam.nxstore.data.AppRepository
import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.SortMode
import com.nxteam.nxstore.model.Source
import com.nxteam.nxstore.ui.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(SortMode.RELEVANCE)
    val sort: StateFlow<SortMode> = _sort.asStateFlow()

    private val _sourceFilter = MutableStateFlow<Source?>(null)
    val sourceFilter: StateFlow<Source?> = _sourceFilter.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<AppItem>>>(UiState.Idle)
    val state: StateFlow<UiState<List<AppItem>>> = _state.asStateFlow()

    private var results: List<AppItem> = emptyList()
    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        if (value.isBlank()) {
            results = emptyList()
            _state.value = UiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _state.value = UiState.Loading
            runCatching { AppRepository.search(value) }
                .onSuccess { found ->
                    results = found
                    emit()
                    val enriched = runCatching { AppRepository.enrich(found) }.getOrNull() ?: return@onSuccess
                    results = enriched
                    emit()
                }
                .onFailure { _state.value = UiState.Error(it.message ?: "Search failed") }
        }
    }

    fun onSortChange(mode: SortMode) {
        _sort.value = mode
        if (results.isNotEmpty()) emit()
    }

    fun onSourceFilterChange(source: Source?) {
        _sourceFilter.value = if (_sourceFilter.value == source) null else source
        if (results.isNotEmpty()) emit()
    }

    private fun emit() {
        val filter = _sourceFilter.value
        val visible = if (filter == null) results else results.filter { it.source == filter }
        _state.value = UiState.Success(AppRepository.sort(visible, _query.value, _sort.value))
    }
}
