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

    private lateinit var executor: Executor

    companion object {
        const val AUTH_TYPE_BIOMETRIC = 1
        const val AUTH_TYPE_PIN = 2
    }

    /**
     * Show biometric authentication dialog
     */
    fun authenticateWithBiometric(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Use your fingerprint or face to authenticate",
        description: String = "Authentication is required to access this feature"
    ) = callbackFlow {
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
     * Verify PIN code with detailed logging
     * Now uses a callback function instead of SecurityManager directly
     */
    suspend fun authenticateWithPin(
        enteredPin: String,
        verifyPin: suspend (String) -> Boolean
    ): AuthenticationResult {
        Log.d("AUTH_MGR_PIN", "=== AUTH_MGR: authenticateWithPin START ===")
        Log.d("AUTH_MGR_PIN", "PIN entered: '$enteredPin'")

        return try {
            Log.d("AUTH_MGR_PIN", "Calling verifyPin callback...")
            val isPinCorrect = verifyPin(enteredPin)
            Log.d("AUTH_MGR_PIN", "verifyPin callback returned: $isPinCorrect")

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
}

// Authentication result sealed class
sealed class AuthenticationResult {
    data class Success(val authType: Int) : AuthenticationResult()
    data class Error(val message: String) : AuthenticationResult()
    object PinRequired : AuthenticationResult()
    object Cancelled : AuthenticationResult()
}