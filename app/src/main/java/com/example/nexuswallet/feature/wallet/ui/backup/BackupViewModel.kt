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

    private val _authRequest = MutableStateFlow<Long?>(null)
    val authRequest: StateFlow<Long?> = _authRequest.asStateFlow()

    fun loadMnemonic(walletId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = getMnemonicUseCase(walletId)
            
            when (result) {
                is Result.Success -> {
                    _mnemonic.value = result.data
                }
                is Result.Error -> {
                    val authException = result.throwable as? HardwareAuthRequiredException
                    if (authException != null) {
                        _cryptoObject.value = authException.cryptoObject
                        _authRequest.value = System.currentTimeMillis()
                    } else {
                        _errorMessage.value = result.message
                    }
                }
                Result.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun onBiometricSuccess(walletId: String, result: BiometricPrompt.AuthenticationResult? = null) {
        val cipher = result?.cryptoObject?.cipher
        _cryptoObject.value = null
        _authRequest.value = null
        viewModelScope.launch {
            _isLoading.value = true
            when (val decryptResult = getMnemonicUseCase(walletId, cipher)) {
                is Result.Success -> {
                    _mnemonic.value = decryptResult.data
                }
                is Result.Error -> {
                    _errorMessage.value = decryptResult.message
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun setErrorMessage(message: String) {
        _errorMessage.value = message
    }
}
