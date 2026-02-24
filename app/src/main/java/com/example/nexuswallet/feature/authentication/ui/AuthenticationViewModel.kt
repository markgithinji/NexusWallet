package com.example.nexuswallet.feature.authentication.ui

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.authentication.domain.AuthAction
import com.example.nexuswallet.feature.wallet.data.securityrefactor.RecordAuthenticationUseCase
import com.example.nexuswallet.feature.wallet.data.securityrefactor.VerifyPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result

@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val verifyPinUseCase: VerifyPinUseCase,
    private val recordAuthenticationUseCase: RecordAuthenticationUseCase
) : ViewModel() {

    private val _authenticationState = MutableStateFlow<AuthenticationResult?>(null)
    val authenticationState: StateFlow<AuthenticationResult?> = _authenticationState

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

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
                        _authenticationState.value = AuthenticationResult.Success(AUTH_TYPE_PIN)
                        _showPinDialog.value = false
                    } else {
                        _errorMessage.value = "Incorrect PIN"
                    }
                }
                is Result.Error -> {
                    _errorMessage.value = verifyResult.message
                }
                Result.Loading -> {
                    // Show loading if needed
                }
            }
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            recordAuthenticationUseCase()
            _authenticationState.value = AuthenticationResult.Success(AUTH_TYPE_BIOMETRIC)
        }
    }

    fun setErrorMessage(message: String) {
        _errorMessage.value = message
    }

    fun cancelPinEntry() {
        _showPinDialog.value = false
        _authenticationState.value = null
    }

    fun clearState() {
        _authenticationState.value = null
        _errorMessage.value = null
    }

    companion object {
        const val AUTH_TYPE_BIOMETRIC = 1
        const val AUTH_TYPE_PIN = 2
    }
}

sealed class AuthenticationResult {
    data class Success(val authType: Int) : AuthenticationResult()
    data class Error(val message: String) : AuthenticationResult()
}