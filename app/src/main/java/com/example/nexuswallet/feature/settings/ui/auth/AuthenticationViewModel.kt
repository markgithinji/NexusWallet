package com.example.nexuswallet.feature.settings.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.settings.domain.usecase.IsBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.IsPinSetUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.RecordAuthenticationUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.VerifyPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val verifyPinUseCase: VerifyPinUseCase,
    private val recordAuthenticationUseCase: RecordAuthenticationUseCase,
    private val isPinSetUseCase: IsPinSetUseCase,
    private val isBiometricEnabledUseCase: IsBiometricEnabledUseCase
) : ViewModel() {

    private val _uiEffect = MutableSharedFlow<AuthUiEffect>()
    val uiEffect: SharedFlow<AuthUiEffect> = _uiEffect.asSharedFlow()

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isPinAvailable = MutableStateFlow(false)
    val isPinAvailable: StateFlow<Boolean> = _isPinAvailable.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _authRequest = MutableStateFlow<Long?>(null)
    val authRequest: StateFlow<Long?> = _authRequest.asStateFlow()

    private val _cryptoObject = MutableStateFlow<Cipher?>(null)
    val cryptoObject: StateFlow<Cipher?> = _cryptoObject.asStateFlow()

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
                        // Dismiss dialog BEFORE emitting navigation effect
                        // to ensure clean transition
                        _showPinDialog.value = false
                        _uiEffect.emit(AuthUiEffect.Authenticated)
                    } else {
                        _errorMessage.value = "Incorrect PIN"
                    }
                }
                is Result.Error -> {
                    val authException = verifyResult.throwable as? HardwareAuthRequiredException
                    if (authException != null) {
                        _cryptoObject.value = authException.cryptoObject?.cipher
                        _authRequest.value = System.currentTimeMillis()
                    } else {
                        _errorMessage.value = verifyResult.message
                    }
                }
                Result.Loading -> {}
            }
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            recordAuthenticationUseCase()
            _authRequest.value = null
            _cryptoObject.value = null
            _uiEffect.emit(AuthUiEffect.Authenticated)
        }
    }

    fun triggerBiometric() {
        _authRequest.value = System.currentTimeMillis()
    }

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun cancelPinEntry() {
        _showPinDialog.value = false
    }

    fun clearState() {
        _showPinDialog.value = false
        _errorMessage.value = null
        _authRequest.value = null
        _cryptoObject.value = null
    }

    fun clearAuthRequest() {
        _authRequest.value = null
    }
}
