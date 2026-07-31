package com.example.nexuswallet.feature.settings.ui.auth

import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.settings.domain.model.AuthType
import com.example.nexuswallet.feature.settings.domain.usecase.RecordAuthenticationUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.VerifyPinUseCase
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.settings.domain.usecase.IsBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.IsPinSetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val verifyPinUseCase: VerifyPinUseCase,
    private val recordAuthenticationUseCase: RecordAuthenticationUseCase,
    private val isPinSetUseCase: IsPinSetUseCase,
    private val isBiometricEnabledUseCase: IsBiometricEnabledUseCase
) : ViewModel() {

    private val _authenticationResult = MutableStateFlow<Result<AuthType>?>(null)
    val authenticationResult: StateFlow<Result<AuthType>?> = _authenticationResult.asStateFlow()

    private val _cryptoObject = MutableStateFlow<BiometricPrompt.CryptoObject?>(null)
    val cryptoObject: StateFlow<BiometricPrompt.CryptoObject?> = _cryptoObject.asStateFlow()

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isPinAvailable = MutableStateFlow(false)
    val isPinAvailable: StateFlow<Boolean> = _isPinAvailable.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    init {
        observeAuthStatus()
    }

    private fun observeAuthStatus() {
        viewModelScope.launch {
            isPinSetUseCase()
                .onEach { isPinSet ->
                    _isPinAvailable.value = isPinSet
                }
                .catch { e ->
                    _isPinAvailable.value = false
                    _errorMessage.value = "Failed to check PIN status: ${e.message}"
                }
                .launchIn(viewModelScope)
        }

        viewModelScope.launch {
            isBiometricEnabledUseCase()
                .onEach { isEnabled ->
                    _isBiometricEnabled.value = isEnabled
                }
                .catch { e ->
                    _isBiometricEnabled.value = false
                }
                .launchIn(viewModelScope)
        }
    }

    fun showPinDialog() {
        _showPinDialog.value = true
        _errorMessage.value = null
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            when (val verifyResult = verifyPinUseCase(pin)) {
                is Result.Success -> {
                    if (verifyResult.data) {
                        recordAuthenticationUseCase()
                        _authenticationResult.value = Result.Success(AuthType.PIN)
                        _showPinDialog.value = false
                    } else {
                        _errorMessage.value = "Incorrect PIN"
                    }
                }
                is Result.Error -> {
                    val authException = verifyResult.throwable as? HardwareAuthRequiredException
                    if (authException != null) {
                        _cryptoObject.value = authException.cryptoObject
                    } else {
                        _errorMessage.value = verifyResult.message
                    }
                }
                Result.Loading -> {}
            }
        }
    }

    fun onBiometricSuccess(result: BiometricPrompt.AuthenticationResult) {
        viewModelScope.launch {
            recordAuthenticationUseCase()
            _authenticationResult.value = Result.Success(AuthType.BIOMETRIC)
        }
    }

    fun setCryptoObject(cryptoObject: BiometricPrompt.CryptoObject?) {
        _cryptoObject.value = cryptoObject
    }

    fun setErrorMessage(message: String) {
        _errorMessage.value = message
    }

    fun cancelPinEntry() {
        _showPinDialog.value = false
        _authenticationResult.value = null
    }

    fun clearState() {
        _authenticationResult.value = null
        _errorMessage.value = null
    }
}