package com.example.nexuswallet.feature.wallet.ui.backup

import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.wallet.domain.usecase.GetMnemonicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val getMnemonicUseCase: GetMnemonicUseCase
) : ViewModel() {

    private val _mnemonic = MutableStateFlow<List<String>?>(null)
    val mnemonic: StateFlow<List<String>?> = _mnemonic.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _cryptoObject = MutableStateFlow<BiometricPrompt.CryptoObject?>(null)
    val cryptoObject: StateFlow<BiometricPrompt.CryptoObject?> = _cryptoObject.asStateFlow()

    fun loadMnemonic(walletId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            android.util.Log.d("BackupFlow", "1. loadMnemonic called for wallet: $walletId")
            val result = getMnemonicUseCase(walletId)
            
            when (result) {
                is Result.Success -> {
                    android.util.Log.d("BackupFlow", "2. Success! Mnemonic loaded immediately (Vault was already open or didn't require auth)")
                    _mnemonic.value = result.data
                }
                is Result.Error -> {
                    val authException = result.throwable as? HardwareAuthRequiredException
                    if (authException != null) {
                        android.util.Log.d("BackupFlow", "2. Auth Required! Exposing cryptoObject to UI")
                        _cryptoObject.value = authException.cryptoObject
                    } else {
                        android.util.Log.e("BackupFlow", "2. Error: ${result.message}")
                        _errorMessage.value = result.message
                    }
                }
                Result.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun onBiometricSuccess(walletId: String, result: BiometricPrompt.AuthenticationResult) {
        val cipher = result.cryptoObject?.cipher ?: return
        android.util.Log.d("BackupFlow", "3. Biometric Success! Retrying decryption with unlocked cipher")
        viewModelScope.launch {
            _isLoading.value = true
            when (val decryptResult = getMnemonicUseCase(walletId, cipher)) {
                is Result.Success -> {
                    android.util.Log.d("BackupFlow", "4. Decryption successful after biometric scan")
                    _mnemonic.value = decryptResult.data
                    _cryptoObject.value = null
                }
                is Result.Error -> {
                    android.util.Log.e("BackupFlow", "4. Decryption failed after biometric scan: ${decryptResult.message}")
                    _errorMessage.value = decryptResult.message
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }
}
