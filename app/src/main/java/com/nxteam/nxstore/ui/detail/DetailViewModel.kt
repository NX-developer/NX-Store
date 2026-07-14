package com.nxteam.nxstore.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nxteam.nxstore.data.AppRepository
import com.nxteam.nxstore.install.Installer
import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.ui.NavSelection
import com.nxteam.nxstore.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val progress: Float) : InstallState
    data object Installing : InstallState
    data object AwaitingConfirm : InstallState
    data class Failed(val message: String) : InstallState
}

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val _item = MutableStateFlow<UiState<AppItem>>(UiState.Loading)
    val item: StateFlow<UiState<AppItem>> = _item.asStateFlow()

    private val _install = MutableStateFlow<InstallState>(InstallState.Idle)
    val install: StateFlow<InstallState> = _install.asStateFlow()

    init { enrich() }

    private fun enrich() {
        val base = NavSelection.current
        if (base == null) {
            _item.value = UiState.Error("No app selected")
            return
        }
        _item.value = UiState.Loading
        viewModelScope.launch {
            runCatching { AppRepository.details(base) }
                .onSuccess { _item.value = UiState.Success(it) }
                .onFailure { _item.value = UiState.Success(base) }
        }
    }

    fun install(item: AppItem) {
        val url = item.downloadUrl ?: return
        _install.value = InstallState.Downloading(0f)
        viewModelScope.launch {
            runCatching {
                val apk = Installer.downloadApk(
                    context = getApplication(),
                    url = url,
                    packageName = item.packageName
                ) { progress -> _install.value = InstallState.Downloading(progress) }
                _install.value = InstallState.Installing
                Installer.install(getApplication(), apk, item.packageName)
            }.onSuccess { result ->
                _install.value = when (result) {
                    is Installer.Result.PendingUserAction -> InstallState.AwaitingConfirm
                    is Installer.Result.Failed -> InstallState.Failed(result.message)
                }
            }.onFailure {
                _install.value = InstallState.Failed(it.message ?: "Install failed")
            }
        }
    }
}
