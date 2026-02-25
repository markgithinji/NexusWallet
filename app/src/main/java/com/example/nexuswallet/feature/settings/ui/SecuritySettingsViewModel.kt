package com.example.nexuswallet.feature.settings.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val getAuthStatusUseCase: GetAuthStatusUseCase,
    private val setBiometricEnabledUseCase: SetBiometricEnabledUseCase,
    private val setPinUseCase: SetPinUseCase,
    private val clearPinUseCase: ClearPinUseCase,
    private val clearAllSecurityDataUseCase: ClearAllSecurityDataUseCase
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<Result<SecurityUiState>>(Result.Loading)
    val uiState: StateFlow<Result<SecurityUiState>> = _uiState.asStateFlow()

    // Dialog states
    private val _showPinSetupDialog = MutableStateFlow(false)
    val showPinSetupDialog: StateFlow<Boolean> = _showPinSetupDialog.asStateFlow()

    private val _showPinChangeDialog = MutableStateFlow(false)
    val showPinChangeDialog: StateFlow<Boolean> = _showPinChangeDialog.asStateFlow()

    private val _pinSetupError = MutableStateFlow<String?>(null)
    val pinSetupError: StateFlow<String?> = _pinSetupError.asStateFlow()

    // Operation state for loading overlays
    private val _operationState = MutableStateFlow<SecurityOperation>(SecurityOperation.IDLE)
    val operationState: StateFlow<SecurityOperation> = _operationState.asStateFlow()

    init {
        loadSecurityStatus()
    }

    private fun loadSecurityStatus() {
        viewModelScope.launch {
            _uiState.value = Result.Loading

            when (val result = getAuthStatusUseCase()) {
                is Result.Success -> {
                    val status = result.data
                    _uiState.value = Result.Success(
                        SecurityUiState(
                            isBiometricEnabled = status.isBiometricEnabled,
                            isPinSet = status.isPinSet,
                            availableAuthMethods = status.availableMethods,
                            isAnyAuthEnabled = status.isAnyAuthEnabled
                        )
                    )
                }
                is Result.Error -> {
                    _uiState.value = Result.Error(result.message)
                }
                Result.Loading -> { /* Ignore */ }
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING

            when (val result = setBiometricEnabledUseCase(enabled)) {
                is Result.Success -> {
                    refreshAuthStatus()
                }
                is Result.Error -> {
                    Log.e("SecurityVM", "Failed to set biometric: ${result.message}")
                }
                Result.Loading -> { /* Ignore */ }
            }

            _operationState.value = SecurityOperation.IDLE
        }
    }

    private suspend fun refreshAuthStatus() {
        when (val result = getAuthStatusUseCase()) {
            is Result.Success -> {
                val status = result.data
                _uiState.update { currentState ->
                    when (currentState) {
                        is Result.Success -> {
                            val updatedState = currentState.data.copy(
                                isBiometricEnabled = status.isBiometricEnabled,
                                isPinSet = status.isPinSet,
                                availableAuthMethods = status.availableMethods,
                                isAnyAuthEnabled = status.isAnyAuthEnabled
                            )
                            Result.Success(updatedState)
                        }
                        else -> currentState
                    }
                }
            }
            is Result.Error -> {
                Log.e("SecurityVM", "Failed to refresh auth status: ${result.message}")
            }
            Result.Loading -> { /* Ignore */ }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.BACKING_UP
            // TODO: Implement backup logic
            delay(2000)
            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.RESTORING
            // TODO: Implement restore logic
            delay(2000)
            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun deleteBackup() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING
            // TODO: Implement delete backup logic
            delay(1000)
            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING

            when (val result = clearAllSecurityDataUseCase()) {
                is Result.Success -> {
                    refreshAuthStatus()
                }
                is Result.Error -> {
                    Log.e("SecurityVM", "Failed to clear all data: ${result.message}")
                }
                Result.Loading -> { /* Ignore */ }
            }

            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun setupPin() {
        _showPinSetupDialog.value = true
        _pinSetupError.value = null
    }

    suspend fun setNewPin(pin: String): Boolean {
        _operationState.value = SecurityOperation.UPDATING

        return try {
            when (val result = setPinUseCase(pin)) {
                is Result.Success -> {
                    if (result.data) {
                        refreshAuthStatus()
                        _showPinSetupDialog.value = false
                        true
                    } else {
                        _pinSetupError.value = "Failed to set PIN"
                        false
                    }
                }
                is Result.Error -> {
                    _pinSetupError.value = "Failed to set PIN: ${result.message}"
                    false
                }
                Result.Loading -> {
                    _pinSetupError.value = "Setting PIN..."
                    false
                }
            }
        } catch (e: Exception) {
            _pinSetupError.value = "Failed to set PIN: ${e.message}"
            false
        } finally {
            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun changePin() {
        _showPinChangeDialog.value = true
        _pinSetupError.value = null
    }

    fun removePin() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING

            when (val result = clearPinUseCase()) {
                is Result.Success -> {
                    refreshAuthStatus()
                }
                is Result.Error -> {
                    Log.e("SecurityVM", "Failed to remove PIN: ${result.message}")
                }
                Result.Loading -> { /* Ignore */ }
            }

            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun cancelPinSetup() {
        _showPinSetupDialog.value = false
        _showPinChangeDialog.value = false
        _pinSetupError.value = null
    }

    fun clearError() {
        _uiState.update { currentState ->
            when (currentState) {
                is Result.Error -> Result.Loading
                else -> currentState
            }
        }
    }

    fun retry() {
        loadSecurityStatus()
    }
}