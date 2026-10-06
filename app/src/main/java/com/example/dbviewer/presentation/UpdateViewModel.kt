package com.example.dbviewer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dbviewer.data.PreferencesStore
import com.example.dbviewer.data.UpdateChecker
import com.example.dbviewer.data.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    object NoUpdate : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class UpdateViewModel(
    private val updateChecker: UpdateChecker,
    private val preferencesStore: PreferencesStore,
    private val currentVersion: String
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    private val _showFirstTimeDialog = MutableStateFlow(false)
    val showFirstTimeDialog = _showFirstTimeDialog.asStateFlow()

    private val _autoCheckEnabled = MutableStateFlow(false)
    val autoCheckEnabled = _autoCheckEnabled.asStateFlow()

    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesStore.autoCheckUpdates.collect { enabled ->
                _autoCheckEnabled.value = enabled
            }
        }
    }

    fun checkForUpdates() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _updateState.value = UpdateState.Checking

            updateChecker.checkForUpdate(currentVersion).fold(
                onSuccess = { updateInfo ->
                    _updateState.value = if (updateInfo != null) {
                        UpdateState.Available(updateInfo)
                    } else {
                        UpdateState.NoUpdate
                    }
                    preferencesStore.updateLastCheckTime()
                },
                onFailure = { error ->
                    _updateState.value = UpdateState.Error(
                        error.message ?: "检查更新失败"
                    )
                }
            )
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesStore.setAutoCheckUpdates(enabled)
        }
    }

    fun showFirstTimeDialog() {
        _showFirstTimeDialog.value = true
    }

    fun dismissFirstTimeDialog() {
        _showFirstTimeDialog.value = false
    }

    fun shouldCheckOnStartup(): Boolean {
        return _autoCheckEnabled.value
    }
}
