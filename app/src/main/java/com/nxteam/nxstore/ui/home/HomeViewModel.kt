package com.nxteam.nxstore.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nxteam.nxstore.data.AppRepository
import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<AppItem>>>(UiState.Idle)
    val state: StateFlow<UiState<List<AppItem>>> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            runCatching { AppRepository.featured() }
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Failed to load") }
        }
    }
}
