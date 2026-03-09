package com.example.nexuswallet.feature.authentication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.authentication.domain.AuthType
import com.example.nexuswallet.feature.authentication.domain.RecordAuthenticationUseCase
import com.example.nexuswallet.feature.authentication.domain.VerifyPinUseCase
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.settings.ui.IsPinSetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val verifyPinUseCase: VerifyPinUseCase,
    private val recordAuthenticationUseCase: RecordAuthenticationUseCase,
    private val isPinSetUseCase: IsPinSetUseCase
) : ViewModel() {

    private val _authenticationResult = MutableStateFlow<Result<AuthType>?>(null)
    val authenticationResult: StateFlow<Result<AuthType>?> = _authenticationResult.asStateFlow()

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isPinAvailable = MutableStateFlow(false)
    val isPinAvailable: StateFlow<Boolean> = _isPinAvailable.asStateFlow()

    init {
        checkPinAvailability()
    }

    private fun checkPinAvailability() {
        viewModelScope.launch {
            when (val result = isPinSetUseCase()) {
                is Result.Success -> {
                    _isPinAvailable.value = result.data
                }
                is Result.Error -> {
                    _isPinAvailable.value = false
                    _errorMessage.value = "Failed to check PIN status"
                }
                Result.Loading -> {
                    // Do nothing
                }
            }
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
                    _errorMessage.value = verifyResult.message
                }

                Result.Loading -> {
                    // NO-OP
                }
            }
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            recordAuthenticationUseCase()
            _authenticationResult.value = Result.Success(AuthType.BIOMETRIC)
        }
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