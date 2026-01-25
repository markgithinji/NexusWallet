package com.example.nexuswallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SecuritySettingsViewModel : ViewModel() {
    private val securityManager = NexusWalletApplication.instance.securityManager

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


    init {
        loadSecurityStatus()
    }

    private fun loadSecurityStatus() {
        viewModelScope.launch {
            _isBiometricEnabled.value = securityManager.isBiometricEnabled()
            _isPinSet.value = securityManager.isPinSet()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityManager.setBiometricEnabled(enabled)
            _isBiometricEnabled.value = enabled
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _securityState.value = SecurityState.BACKING_UP
            // TODO: Backup logic
            _securityState.value = SecurityState.IDLE
            _isBackupAvailable.value = true
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            _securityState.value = SecurityState.RESTORING
            // TODO: Restore logic
            _securityState.value = SecurityState.IDLE
        }
    }

    fun deleteBackup() {
        viewModelScope.launch {
            // TODO: Delete backup logic
            _isBackupAvailable.value = false
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            securityManager.clearAll()
            _isBiometricEnabled.value = false
            _isPinSet.value = false
            _isBackupAvailable.value = false
        }
    }

    fun setupPin() {
        _showPinSetupDialog.value = true
        _pinSetupError.value = null
    }

    suspend fun setNewPin(pin: String): Boolean {
        return try {
            val success = securityManager.setPin(pin)
            if (success) {
                _isPinSet.value = true
                _showPinSetupDialog.value = false
            }
            success
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
            securityManager.clearPin()
            _isPinSet.value = false
        }
    }

    fun cancelPinSetup() {
        _showPinSetupDialog.value = false
        _showPinChangeDialog.value = false
        _pinSetupError.value = null
    }
}