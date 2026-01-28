package com.example.nexuswallet.feature.authentication.ui

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.authentication.domain.AuthAction
import com.example.nexuswallet.feature.authentication.domain.AuthenticationManager
import com.example.nexuswallet.feature.authentication.domain.AuthenticationResult
import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val authenticationManager = AuthenticationManager(context)

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

    suspend fun verifyPin(pin: String) {
        val result = authenticationManager.authenticateWithPin(pin, securityManager)
        _authenticationState.value = result

        _showPinDialog.value = false

        if (result is AuthenticationResult.Error) {
            _errorMessage.value = result.message
        } else if (result is AuthenticationResult.Success) {

            securityManager.recordAuthentication()
        }
    }

    fun recordAuthentication() {
        securityManager.recordAuthentication()
    }

    fun authenticateWithBiometric(activity: FragmentActivity) {
        viewModelScope.launch {
            try {
                authenticationManager.authenticateWithBiometric(
                    activity = activity,
                    title = "Authenticate",
                    subtitle = "Use your fingerprint or face to authenticate",
                    description = "Authentication is required to access this feature"
                ).collect { result ->
                    _authenticationState.value = result

                    if (result is AuthenticationResult.Success) {
                        recordAuthentication()
                    }
                }
            } catch (e: Exception) {
                _authenticationState.value = AuthenticationResult.Error("Biometric error: ${e.message}")
            }
        }
    }

    fun cancelPinEntry() {
        _showPinDialog.value = false
        _authenticationState.value = AuthenticationResult.Cancelled
    }

    fun setErrorMessage(message: String) {
        _errorMessage.value = message
    }

    fun clearError() {
        _errorMessage.value = null
    }
}