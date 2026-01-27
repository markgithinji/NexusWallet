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

    private val _authenticationRequired = MutableStateFlow<AuthCheck?>(null)
    val authenticationRequired: StateFlow<AuthCheck?> = _authenticationRequired

    data class AuthCheck(
        val required: Boolean,
        val action: AuthAction,
        val targetId: String? = null
    )

    // Check if authentication is required for an action
    fun checkAuthenticationRequired(action: AuthAction, targetId: String? = null) {
        viewModelScope.launch {
            val required = securityManager.isAuthenticationRequired(action)
            _authenticationRequired.value = AuthCheck(required, action, targetId)
        }
    }

    // Clear the authentication required state
    fun clearAuthCheck() {
        _authenticationRequired.value = null
    }

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
        } else if (result is AuthenticationResult.Success) {
            Log.d("AUTH_VM_DEBUG", " PIN success!")
            Log.d("AUTH_VM_DEBUG", "Auth type: ${result.authType}")

            // Record the authentication using the injected securityManager
            securityManager.recordAuthentication()
            Log.d("AUTH_VM_DEBUG", "Authentication recorded via securityManager")

            // Clear any pending auth check since we just authenticated
            clearAuthCheck()
        }

        Log.d("AUTH_VM_DEBUG", "=== VERIFY PIN END ===")
    }

    // Add this method to record biometric authentication
    fun recordAuthentication() {
        securityManager.recordAuthentication()
        // Clear any pending auth check since we just authenticated
        clearAuthCheck()
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