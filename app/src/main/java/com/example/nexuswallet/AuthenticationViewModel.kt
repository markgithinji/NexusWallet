package com.example.nexuswallet

import android.app.Activity
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthenticationViewModel : ViewModel() {

    private val authenticationManager = AuthenticationManager(NexusWalletApplication.instance)
    private val securityManager = NexusWalletApplication.instance.securityManager

    private val _authenticationState = MutableStateFlow<AuthenticationResult?>(null)
    val authenticationState: StateFlow<AuthenticationResult?> = _authenticationState

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun showPinDialog() {
        Log.d("AUTH_VM_DEBUG", "🎬 showPinDialog() called - setting to true")
        _showPinDialog.value = true
        _errorMessage.value = null
    }

    suspend fun verifyPin(pin: String) {
        Log.d("AUTH_VM_DEBUG", "=== VERIFY PIN START ===")
        Log.d("AUTH_VM_DEBUG", "PIN received: '$pin' (length: ${pin.length})")

        Log.d("AUTH_VM_DEBUG", "Calling authenticationManager.authenticateWithPin()...")
        val result = authenticationManager.authenticateWithPin(pin, securityManager)

        Log.d("AUTH_VM_DEBUG", "Result from authenticationManager: ${result::class.simpleName}")

        _authenticationState.value = result

        // DEBUG: Check if we're closing the dialog too soon
        Log.d("AUTH_VM_DEBUG", "Setting _showPinDialog.value = false")
        _showPinDialog.value = false
        Log.d("AUTH_VM_DEBUG", "_showPinDialog is now: false")

        if (result is AuthenticationResult.Error) {
            Log.d("AUTH_VM_DEBUG", " PIN error: ${result.message}")
            _errorMessage.value = result.message
            // Should we show the dialog again for wrong PIN?
            // Maybe we should NOT close the dialog on error?
        } else if (result is AuthenticationResult.Success) {
            Log.d("AUTH_VM_DEBUG", " PIN success!")
            Log.d("AUTH_VM_DEBUG", "Auth type: ${result.authType}")

            // Record the authentication
            securityManager.recordAuthentication()
            Log.d("AUTH_VM_DEBUG", "Authentication recorded")
        }

        Log.d("AUTH_VM_DEBUG", "=== VERIFY PIN END ===")
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
                }
            } catch (e: Exception) {
                _authenticationState.value = AuthenticationResult.Error("Biometric error: ${e.message}")
            }
        }
    }

    fun cancelPinEntry() {
        Log.d("AUTH_VM_DEBUG", " cancelPinEntry() called")
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