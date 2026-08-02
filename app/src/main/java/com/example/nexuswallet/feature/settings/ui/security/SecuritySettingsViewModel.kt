package com.example.nexuswallet.feature.settings.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.settings.domain.usecase.ClearAllSecurityDataUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.ClearPinUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.GetAuthStatusUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.SetBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.SetPinUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val getAuthStatusUseCase: GetAuthStatusUseCase,
    private val setBiometricEnabledUseCase: SetBiometricEnabledUseCase,
    private val securityRepository: SecurityRepository,
    private val setPinUseCase: SetPinUseCase,
    private val clearPinUseCase: ClearPinUseCase,
    private val clearAllSecurityDataUseCase: ClearAllSecurityDataUseCase
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<Result<SecurityUiState>>(Result.Loading)
    val uiState: StateFlow<Result<SecurityUiState>> = _uiState.asStateFlow()

    // UI Effects
    private val _uiEffect = MutableSharedFlow<SecurityUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    // Dialog states
    private val _showPinSetupDialog = MutableStateFlow(false)
    val showPinSetupDialog: StateFlow<Boolean> = _showPinSetupDialog.asStateFlow()

    private val _showPinChangeDialog = MutableStateFlow(false)
    val showPinChangeDialog: StateFlow<Boolean> = _showPinChangeDialog.asStateFlow()

    private val _showClearAllDataDialog = MutableStateFlow(false)
    val showClearAllDataDialog: StateFlow<Boolean> = _showClearAllDataDialog.asStateFlow()

    private val _clearAllConfirmationText = MutableStateFlow("")
    val clearAllConfirmationText: StateFlow<String> = _clearAllConfirmationText.asStateFlow()

    private val _authRequest = MutableStateFlow<Long?>(null)
    val authRequest: StateFlow<Long?> = _authRequest.asStateFlow()

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
                            isPrivacyModeEnabled = status.isPrivacyModeEnabled,
                            isRequireAuthForSend = status.isRequireAuthForSend,
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
            when (val result = setBiometricEnabledUseCase(enabled)) {
                is Result.Success -> {
                    refreshAuthStatus()
                }
                is Result.Error -> {
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
                }
                Result.Loading -> { /* Ignore */ }
            }
        }
    }

    fun setPrivacyModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                securityRepository.setPrivacyModeEnabled(enabled)
                refreshAuthStatus()
            } catch (e: Exception) {
                _uiEffect.emit(SecurityUiEffect.ShowSnackbar(e.message ?: "Failed to update privacy mode"))
            }
        }
    }

    fun setRequireAuthForSend(enabled: Boolean) {
        viewModelScope.launch {
            try {
                securityRepository.setRequireAuthForSend(enabled)
                refreshAuthStatus()
            } catch (e: Exception) {
                _uiEffect.emit(SecurityUiEffect.ShowSnackbar(e.message ?: "Failed to update security preference"))
            }
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
                                isPrivacyModeEnabled = status.isPrivacyModeEnabled,
                                isRequireAuthForSend = status.isRequireAuthForSend,
                                availableAuthMethods = status.availableMethods,
                                isAnyAuthEnabled = status.isAnyAuthEnabled
                            )
                            Result.Success(updatedState)
                        }
                        else -> {
                            // If we weren't in success state, transition to it
                            Result.Success(
                                SecurityUiState(
                                    isBiometricEnabled = status.isBiometricEnabled,
                                    isPinSet = status.isPinSet,
                                    isPrivacyModeEnabled = status.isPrivacyModeEnabled,
                                    isRequireAuthForSend = status.isRequireAuthForSend,
                                    availableAuthMethods = status.availableMethods,
                                    isAnyAuthEnabled = status.isAnyAuthEnabled
                                )
                            )
                        }
                    }
                }
            }
            is Result.Error -> {
                _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
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

    fun requestClearAllData() {
        _showClearAllDataDialog.value = true
        _clearAllConfirmationText.value = ""
    }

    fun onClearAllConfirmationTextChanged(text: String) {
        _clearAllConfirmationText.value = text
    }

    fun cancelClearAllData() {
        _showClearAllDataDialog.value = false
        _clearAllConfirmationText.value = ""
    }

    fun confirmClearAllData() {
        if (_clearAllConfirmationText.value.trim().uppercase() != "DELETE") {
            return
        }
        _showClearAllDataDialog.value = false
        
        // Trigger authentication request for the UI
        _authRequest.value = System.currentTimeMillis()
    }

    fun onClearAllAuthSuccess() {
        _authRequest.value = null
        clearAllData()
    }

    private fun clearAllData() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING

            when (val result = clearAllSecurityDataUseCase()) {
                is Result.Success -> {
                    refreshAuthStatus()
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar("All security data cleared"))
                }
                is Result.Error -> {
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
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

    fun setNewPin(pin: String) {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING

            when (val result = setPinUseCase(pin)) {
                is Result.Success -> {
                    if (result.data) {
                        refreshAuthStatus()
                        _showPinSetupDialog.value = false
                        _showPinChangeDialog.value = false
                        _uiEffect.emit(SecurityUiEffect.ShowSnackbar("PIN set successfully"))
                    } else {
                        _pinSetupError.value = "Failed to set PIN"
                    }
                }
                is Result.Error -> {
                    _pinSetupError.value = "Failed to set PIN: ${result.message}"
                }
                Result.Loading -> {
                    _pinSetupError.value = "Setting PIN..."
                }
            }

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
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar("PIN removed"))
                }
                is Result.Error -> {
                    _uiEffect.emit(SecurityUiEffect.ShowSnackbar(result.message))
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