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

    fun setupPin() {
        // Show PIN setup dialog
    }

    fun changePin() {
        // Show PIN change dialog
    }

    fun removePin() {
        viewModelScope.launch {
            // Remove PIN logic
            _isPinSet.value = false
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _securityState.value = SecurityState.BACKING_UP
            // Backup logic
            _securityState.value = SecurityState.IDLE
            _isBackupAvailable.value = true
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            _securityState.value = SecurityState.RESTORING
            // Restore logic
            _securityState.value = SecurityState.IDLE
        }
    }

    fun deleteBackup() {
        viewModelScope.launch {
            // Delete backup logic
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
}