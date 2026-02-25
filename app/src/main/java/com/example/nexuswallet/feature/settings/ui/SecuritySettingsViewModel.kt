package com.example.nexuswallet.feature.settings.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.wallet.data.securityrefactor.ClearAllSecurityDataUseCase
import com.example.nexuswallet.feature.wallet.data.securityrefactor.ClearPinUseCase
import com.example.nexuswallet.feature.wallet.data.securityrefactor.GetAvailableAuthMethodsUseCase
import com.example.nexuswallet.feature.wallet.data.securityrefactor.IsAnyAuthEnabledUseCase
import com.example.nexuswallet.feature.wallet.data.securityrefactor.IsBiometricEnabledUseCase
import com.example.nexuswallet.feature.wallet.data.securityrefactor.IsPinSetUseCase
import com.example.nexuswallet.feature.wallet.data.securityrefactor.SetBiometricEnabledUseCase
import com.example.nexuswallet.feature.wallet.data.securityrefactor.SetPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.securityrefactor.AuthMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val isBiometricEnabledUseCase: IsBiometricEnabledUseCase,
    private val setBiometricEnabledUseCase: SetBiometricEnabledUseCase,
    private val isPinSetUseCase: IsPinSetUseCase,
    private val setPinUseCase: SetPinUseCase,
    private val clearPinUseCase: ClearPinUseCase,
    private val clearAllSecurityDataUseCase: ClearAllSecurityDataUseCase,
    private val isAnyAuthEnabledUseCase: IsAnyAuthEnabledUseCase,
    private val getAvailableAuthMethodsUseCase: GetAvailableAuthMethodsUseCase
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

            // Load all security statuses in parallel
            val bioDeferred = async { isBiometricEnabledUseCase() }
            val pinDeferred = async { isPinSetUseCase() }
            val methodsDeferred = async { getAvailableAuthMethodsUseCase() }
            val anyAuthDeferred = async { isAnyAuthEnabledUseCase() }

            val bioResult = bioDeferred.await()
            val pinResult = pinDeferred.await()
            val methodsResult = methodsDeferred.await()
            val anyAuthResult = anyAuthDeferred.await()

            // Check for errors
            val errors = listOfNotNull(
                (bioResult as? Result.Error)?.let { "Biometric: ${it.message}" },
                (pinResult as? Result.Error)?.let { "PIN: ${it.message}" },
                (methodsResult as? Result.Error)?.let { "Methods: ${it.message}" },
                (anyAuthResult as? Result.Error)?.let { "Auth: ${it.message}" }
            )

            if (errors.isNotEmpty()) {
                _uiState.value = Result.Error("Failed to load security status: ${errors.joinToString(", ")}")
                return@launch
            }

            // Extract successful results
            val securityState = SecurityUiState(
                isBiometricEnabled = (bioResult as Result.Success).data,
                isPinSet = (pinResult as Result.Success).data,
                availableAuthMethods = (methodsResult as Result.Success).data,
                isAnyAuthEnabled = (anyAuthResult as Result.Success).data
            )

            _uiState.value = Result.Success(securityState)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING

            when (val result = setBiometricEnabledUseCase(enabled)) {
                is Result.Success -> {
                    // Update the current UI state
                    _uiState.update { currentState ->
                        when (currentState) {
                            is Result.Success -> {
                                val updatedState = currentState.data.copy(
                                    isBiometricEnabled = enabled,
                                    isAnyAuthEnabled = enabled || currentState.data.isPinSet
                                )
                                Result.Success(updatedState)
                            }
                            else -> currentState
                        }
                    }
                    // Refresh auth methods
                    refreshAuthMethods()
                }
                is Result.Error -> {
                    Log.e("SecurityVM", "Failed to set biometric: ${result.message}")
                }
                Result.Loading -> { /* Ignore */ }
            }

            _operationState.value = SecurityOperation.IDLE
        }
    }

    private suspend fun refreshAuthMethods() {
        when (val methodsResult = getAvailableAuthMethodsUseCase()) {
            is Result.Success -> {
                _uiState.update { currentState ->
                    when (currentState) {
                        is Result.Success -> {
                            val updatedState = currentState.data.copy(
                                availableAuthMethods = methodsResult.data
                            )
                            Result.Success(updatedState)
                        }
                        else -> currentState
                    }
                }
            }
            is Result.Error -> {
                Log.e("SecurityVM", "Failed to refresh auth methods: ${methodsResult.message}")
            }
            Result.Loading -> { /* Ignore */ }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.BACKING_UP
            // TODO: Implement backup logic using backup usecases when available
            delay(2000) // Simulate work
            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.RESTORING
            // TODO: Implement restore logic using backup usecases when available
            delay(2000) // Simulate work
            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun deleteBackup() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING
            // TODO: Implement delete backup logic
            delay(1000) // Simulate work
            _operationState.value = SecurityOperation.IDLE
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _operationState.value = SecurityOperation.UPDATING

            when (val result = clearAllSecurityDataUseCase()) {
                is Result.Success -> {
                    // Reset UI state
                    _uiState.update { currentState ->
                        when (currentState) {
                            is Result.Success -> {
                                val updatedState = currentState.data.copy(
                                    isBiometricEnabled = false,
                                    isPinSet = false
                                )
                                Result.Success(updatedState)
                            }
                            else -> currentState
                        }
                    }
                    // Refresh auth methods
                    refreshAuthMethods()
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
                        // Update UI state
                        _uiState.update { currentState ->
                            when (currentState) {
                                is Result.Success -> {
                                    val updatedState = currentState.data.copy(
                                        isPinSet = true,
                                        isAnyAuthEnabled = true
                                    )
                                    Result.Success(updatedState)
                                }
                                else -> currentState
                            }
                        }
                        _showPinSetupDialog.value = false
                        // Refresh auth methods
                        refreshAuthMethods()
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
                    // Update UI state
                    _uiState.update { currentState ->
                        when (currentState) {
                            is Result.Success -> {
                                val updatedState = currentState.data.copy(
                                    isPinSet = false,
                                    isAnyAuthEnabled = currentState.data.isBiometricEnabled
                                )
                                Result.Success(updatedState)
                            }
                            else -> currentState
                        }
                    }
                    // Refresh auth methods
                    refreshAuthMethods()
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