package com.nxteam.nxstore.ui

import com.nxteam.nxstore.model.AppItem

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

object NavSelection {
    @Volatile var current: AppItem? = null
}
