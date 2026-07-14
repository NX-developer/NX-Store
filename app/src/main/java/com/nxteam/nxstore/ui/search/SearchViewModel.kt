package com.nxteam.nxstore.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxteam.nxstore.data.AppRepository
import com.nxteam.nxstore.model.AppItem
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

    private val _state = MutableStateFlow<UiState<List<AppItem>>>(UiState.Idle)
    val state: StateFlow<UiState<List<AppItem>>> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.value = UiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _state.value = UiState.Loading
            runCatching { AppRepository.search(value) }
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Search failed") }
        }
    }
}
