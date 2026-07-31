package com.example.nexuswallet.feature.core.data.util

import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricPrompt
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.util.Result
import javax.crypto.IllegalBlockSizeException

/**
 * Safely executes a KeyStore operation and maps security exceptions to [Result].
 * Specifically handles [UserNotAuthenticatedException] by returning a [Result.Error] 
 * containing a [HardwareAuthRequiredException].
 * 
 * @param onAuthRequired Optional block to provide a [BiometricPrompt.CryptoObject] if authentication is needed.
 */
inline fun <T> safeKeyStoreCall(
    crossinline onAuthRequired: () -> BiometricPrompt.CryptoObject? = { null },
    block: () -> T
): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Exception) {
        val isAuthRequired = e is UserNotAuthenticatedException ||
                e.cause is UserNotAuthenticatedException ||
                (e is IllegalBlockSizeException && e.message?.contains("user not authenticated", true) == true) ||
                (e.cause?.message?.contains("user not authenticated", true) == true)

        if (isAuthRequired) {
            Result.Error(
                message = "Authentication required",
                throwable = HardwareAuthRequiredException(onAuthRequired())
            )
        } else {
            val message = when (e) {
                is java.security.KeyStoreException -> "KeyStore hardware error"
                is EncryptionException -> e.message ?: "Security operation failed"
                else -> e.message ?: "Encryption/Decryption failed"
            }
            Result.Error(message, e)
        }
    }
}
