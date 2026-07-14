package com.nxteam.nxstore.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxteam.nxstore.data.AppRepository
import com.nxteam.nxstore.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow<UiState<AppRepository.HomeFeed>>(UiState.Idle)
    val state: StateFlow<UiState<AppRepository.HomeFeed>> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            runCatching { AppRepository.home() }
                .onSuccess { feed ->
                    if (feed.isEmpty) {
                        _state.value = UiState.Error("Could not reach any source")
                        return@onSuccess
                    }
                    _state.value = UiState.Success(feed)
                    val enriched = runCatching { AppRepository.enrichHome(feed) }.getOrNull()
                        ?: return@onSuccess
                    _state.value = UiState.Success(enriched)
                }
                .onFailure { _state.value = UiState.Error(it.message ?: "Failed to load") }
        }
    }
}
