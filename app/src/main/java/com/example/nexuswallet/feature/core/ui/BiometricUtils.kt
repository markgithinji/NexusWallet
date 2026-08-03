package com.example.nexuswallet.feature.core.ui

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.example.nexuswallet.R
import javax.crypto.Cipher

/**
 * Remembers a [BiometricPrompt] for use in Compose.
 */
@Composable
fun rememberBiometricPrompt(
    onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
    onError: (Int, CharSequence) -> Unit,
    onFailed: () -> Unit = {}
): BiometricPrompt? {
    val activity = LocalActivity.current as? AppCompatActivity ?: return null
    val context = LocalContext.current

    return remember(activity, onSuccess, onError, onFailed) {
        val executor = ContextCompat.getMainExecutor(context)
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    onFailed()
                }
            }
        )
    }
}

/**
 * Common helper to filter out user cancellations from actual errors.
 */
fun isBiometricUserCancel(errorCode: Int): Boolean {
    return errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
            errorCode == BiometricPrompt.ERROR_CANCELED
}

/**
 * A reusable Composable that handles the biometric authentication flow.
 */
@Composable
fun BiometricAuthHandler(
    authRequest: Any?,
    onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
    onError: (Int, String) -> Unit,
    onDismiss: () -> Unit,
    cryptoObject: Cipher? = null,
    title: String = stringResource(R.string.biometric_authentication),
    subtitle: String? = null,
    negativeButtonText: String = stringResource(R.string.cancel)
) {
    val biometricPrompt = rememberBiometricPrompt(
        onSuccess = onSuccess,
        onError = { code, msg -> onError(code, msg.toString()) }
    )

    LaunchedEffect(authRequest) {
        if (authRequest != null) {
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(negativeButtonText)

            subtitle?.let { builder.setSubtitle(it) }

            val promptInfo = builder.build()

            if (cryptoObject != null) {
                biometricPrompt?.authenticate(promptInfo, BiometricPrompt.CryptoObject(cryptoObject))
            } else {
                biometricPrompt?.authenticate(promptInfo)
            }
            onDismiss()
        }
    }
}
