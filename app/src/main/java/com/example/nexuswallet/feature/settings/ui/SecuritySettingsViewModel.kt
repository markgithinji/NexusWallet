package com.example.nexuswallet.feature.settings.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.authentication.domain.SecurityState
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

    private val _securityState = MutableStateFlow<SecurityState>(SecurityState.IDLE)
    val securityState: StateFlow<SecurityState> = _securityState

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled

    private val _isPinSet = MutableStateFlow(false)
    val isPinSet: StateFlow<Boolean> = _isPinSet

    private val _isBackupAvailable = MutableStateFlow(false)
    val isBackupAvailable: StateFlow<Boolean> = _isBackupAvailable

    private val _showPinSetupDialog = MutableStateFlow(false)
    val showPinSetupDialog: StateFlow<Boolean> = _showPinSetupDialog

    private val _showPinChangeDialog = MutableStateFlow(false)
    val showPinChangeDialog: StateFlow<Boolean> = _showPinChangeDialog

    private val _pinSetupError = MutableStateFlow<String?>(null)
    val pinSetupError: StateFlow<String?> = _pinSetupError

    private val _availableAuthMethods = MutableStateFlow<List<AuthMethod>>(emptyList())
    val availableAuthMethods: StateFlow<List<AuthMethod>> = _availableAuthMethods

    private val _isAnyAuthEnabled = MutableStateFlow(false)
    val isAnyAuthEnabled: StateFlow<Boolean> = _isAnyAuthEnabled

    init {
        loadSecurityStatus()
    }

    private fun loadSecurityStatus() {
        viewModelScope.launch {
            // Load biometric status
            when (val bioResult = isBiometricEnabledUseCase()) {
                is Result.Success -> _isBiometricEnabled.value = bioResult.data
                is Result.Error -> Log.e("SecurityVM", "Failed to load biometric status: ${bioResult.message}")
                Result.Loading -> { /* Ignore */ }
            }

            // Load PIN status
            when (val pinResult = isPinSetUseCase()) {
                is Result.Success -> _isPinSet.value = pinResult.data
                is Result.Error -> Log.e("SecurityVM", "Failed to load PIN status: ${pinResult.message}")
                Result.Loading -> { /* Ignore */ }
            }

            // Load available auth methods
            when (val methodsResult = getAvailableAuthMethodsUseCase()) {
                is Result.Success -> _availableAuthMethods.value = methodsResult.data
                is Result.Error -> Log.e("SecurityVM", "Failed to load auth methods: ${methodsResult.message}")
                Result.Loading -> { /* Ignore */ }
            }

            // Load if any auth is enabled
            when (val anyAuthResult = isAnyAuthEnabledUseCase()) {
                is Result.Success -> _isAnyAuthEnabled.value = anyAuthResult.data
                is Result.Error -> Log.e("SecurityVM", "Failed to check if any auth enabled: ${anyAuthResult.message}")
                Result.Loading -> { /* Ignore */ }
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = setBiometricEnabledUseCase(enabled)) {
                is Result.Success -> {
                    _isBiometricEnabled.value = enabled
                    // Refresh auth methods
                    loadSecurityStatus()
                }
                is Result.Error -> {
                    Log.e("SecurityVM", "Failed to set biometric: ${result.message}")
                }
                Result.Loading -> { /* Ignore */ }
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _securityState.value = SecurityState.BACKING_UP
            // TODO: Implement backup logic using backup usecases when available
            // For example: createEncryptedBackupUseCase(walletId, wallet)
            _securityState.value = SecurityState.IDLE
            _isBackupAvailable.value = true
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            _securityState.value = SecurityState.RESTORING
            // TODO: Implement restore logic using backup usecases when available
            // For example: restoreFromBackupUseCase(walletId)
            _securityState.value = SecurityState.IDLE
        }
    }

    fun deleteBackup() {
        viewModelScope.launch {
            // TODO: Implement delete backup logic
            _isBackupAvailable.value = false
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            when (val result = clearAllSecurityDataUseCase()) {
                is Result.Success -> {
                    _isBiometricEnabled.value = false
                    _isPinSet.value = false
                    _isBackupAvailable.value = false
                    // Refresh auth methods
                    loadSecurityStatus()
                }
                is Result.Error -> {
                    Log.e("SecurityVM", "Failed to clear all data: ${result.message}")
                }
                Result.Loading -> { /* Ignore */ }
            }
        }
    }

    fun setupPin() {
        _showPinSetupDialog.value = true
        _pinSetupError.value = null
    }

    suspend fun setNewPin(pin: String): Boolean {
        return try {
            when (val result = setPinUseCase(pin)) {
                is Result.Success -> {
                    if (result.data) {
                        _isPinSet.value = true
                        _showPinSetupDialog.value = false
                        // Refresh auth methods
                        loadSecurityStatus()
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
        }
    }

    fun changePin() {
        _showPinChangeDialog.value = true
        _pinSetupError.value = null
    }

    fun removePin() {
        viewModelScope.launch {
            when (val result = clearPinUseCase()) {
                is Result.Success -> {
                    _isPinSet.value = false
                    // Refresh auth methods
                    loadSecurityStatus()
                }
                is Result.Error -> {
                    Log.e("SecurityVM", "Failed to remove PIN: ${result.message}")
                }
                Result.Loading -> { /* Ignore */ }
            }
        }
    }

    fun cancelPinSetup() {
        _showPinSetupDialog.value = false
        _showPinChangeDialog.value = false
        _pinSetupError.value = null
    }
}