package com.example.nexuswallet.feature.authentication.domain

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executor

/**
 * Handles biometric and PIN authentication
 */
class AuthenticationManager(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)
    private lateinit var executor: Executor

    companion object {
        const val AUTH_TYPE_BIOMETRIC = 1
        const val AUTH_TYPE_PIN = 2
        const val AUTH_TYPE_NONE = 3

        const val REQUEST_CODE_BIOMETRIC = 1001
        const val REQUEST_CODE_PIN = 1002
    }

    /**
     * Check if biometric authentication is available
     */
    fun isBiometricAvailable(): BiometricStatus {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    /**
     * Show biometric authentication dialog
     */
    fun authenticateWithBiometric(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Use your fingerprint or face to authenticate",
        description: String = "Authentication is required to access this feature"
    ) = callbackFlow<AuthenticationResult> {
        executor = ContextCompat.getMainExecutor(context)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(true)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    trySend(AuthenticationResult.Error(
                        when (errorCode) {
                            BiometricPrompt.ERROR_CANCELED -> "Authentication cancelled"
                            BiometricPrompt.ERROR_USER_CANCELED -> "Authentication cancelled by user"
                            BiometricPrompt.ERROR_LOCKOUT -> "Too many failed attempts. Try again later"
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Biometric locked permanently"
                            else -> "Authentication error: $errString"
                        }
                    ))
                    close()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    trySend(AuthenticationResult.Success(AUTH_TYPE_BIOMETRIC))
                    close()
                }

                override fun onAuthenticationFailed() {
                    trySend(AuthenticationResult.Error("Authentication failed"))
                }
            })

        biometricPrompt.authenticate(promptInfo)

        awaitClose {
        }
    }

    /**
     * Verify PIN code
     */
    /**
     * Verify PIN code with detailed logging
     */
    suspend fun authenticateWithPin(
        enteredPin: String,
        securityManager: SecurityManager
    ): AuthenticationResult {
        Log.d("AUTH_MGR_PIN", "=== AUTH_MGR: authenticateWithPin START ===")
        Log.d("AUTH_MGR_PIN", "PIN entered: '$enteredPin'")

        return try {
            Log.d("AUTH_MGR_PIN", "Calling securityManager.verifyPin('$enteredPin')...")
            val isPinCorrect = securityManager.verifyPin(enteredPin)
            Log.d("AUTH_MGR_PIN", "securityManager.verifyPin returned: $isPinCorrect")

            if (isPinCorrect) {
                Log.d("AUTH_MGR_PIN", " PIN is CORRECT - returning Success")
                AuthenticationResult.Success(AUTH_TYPE_PIN)
            } else {
                Log.d("AUTH_MGR_PIN", " PIN is INCORRECT - returning Error")
                AuthenticationResult.Error("Incorrect PIN")
            }
        } catch (e: Exception) {
            Log.e("AUTH_MGR_PIN", " Exception in authenticateWithPin", e)
            AuthenticationResult.Error("PIN verification failed: ${e.message}")
        } finally {
            Log.d("AUTH_MGR_PIN", " === AUTH_MGR: authenticateWithPin END ===")
        }
    }

    /**
     * Check if authentication is required (based on settings)
     */
    suspend fun isAuthenticationRequired(
        securityManager: SecurityManager,
        requireBiometric: Boolean = true,
        requirePin: Boolean = true
    ): Boolean {
        if (requireBiometric && securityManager.isBiometricEnabled()) {
            return true
        }

        if (requirePin && securityManager.isPinSet()) {
            return true
        }

        return false
    }

    /**
     * Get available authentication methods
     */
    suspend fun getAvailableAuthMethods(
        securityManager: SecurityManager
    ): List<AuthMethod> {
        val methods = mutableListOf<AuthMethod>()

        // Check biometric availability
        val biometricStatus = isBiometricAvailable()
        if (biometricStatus == BiometricStatus.AVAILABLE && securityManager.isBiometricEnabled()) {
            methods.add(AuthMethod.BIOMETRIC)
        }

        // Check if PIN is set
        if (securityManager.isPinSet()) {
            methods.add(AuthMethod.PIN)
        }

        return methods
    }

    /**
     * Show appropriate authentication based on available methods
     */
//    suspend fun authenticate(
//        activity: FragmentActivity,
//        securityManager: SecurityManager,
//        title: String = "Authentication Required",
//        description: String = "Authenticate to proceed"
//    ): AuthenticationResult {
//        val availableMethods = getAvailableAuthMethods(securityManager)
//
//        return when {
//            availableMethods.contains(AuthMethod.BIOMETRIC) -> {
//                // Use biometric if available and enabled
//                authenticateWithBiometric(activity, title, description)
//            }
//            availableMethods.contains(AuthMethod.PIN) -> {
//                // Return special result to show PIN dialog in UI
//                AuthenticationResult.PinRequired
//            }
//            else -> {
//                AuthenticationResult.Error("No authentication method available")
//            }
//        }
//    }
}

// Authentication result sealed class
sealed class AuthenticationResult {
    data class Success(val authType: Int) : AuthenticationResult()
    data class Error(val message: String) : AuthenticationResult()
    object PinRequired : AuthenticationResult()
    object Cancelled : AuthenticationResult()
}

// Biometric status enum
enum class BiometricStatus {
    AVAILABLE,
    NOT_ENROLLED,
    HARDWARE_UNAVAILABLE,
    NO_HARDWARE,
    SECURITY_UPDATE_REQUIRED,
    UNAVAILABLE
}