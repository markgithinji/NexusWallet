package com.example.nexuswallet.feature.core.data.util

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricPrompt
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.util.Result
import javax.crypto.AEADBadTagException
import javax.crypto.IllegalBlockSizeException
import java.security.InvalidKeyException

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
        val isPermanentlyInvalidated = e is KeyPermanentlyInvalidatedException || e.cause is KeyPermanentlyInvalidatedException
        
        val isAuthRequired = !isPermanentlyInvalidated && (
                e is UserNotAuthenticatedException ||
                e.cause is UserNotAuthenticatedException ||
                e is InvalidKeyException ||
                (e is IllegalBlockSizeException && e.message?.contains("user not authenticated", true) == true) ||
                (e.cause?.message?.contains("user not authenticated", true) == true)
        )

        if (isAuthRequired) {
            Result.Error(
                message = "Authentication required",
                throwable = HardwareAuthRequiredException(onAuthRequired())
            )
        } else {
            val message = when {
                isPermanentlyInvalidated -> "Security key invalidated (biometrics changed). Please restore your wallet using your seed phrase."
                e is AEADBadTagException -> "Decryption failed (integrity check failed). Data may be corrupted or the key has changed."
                e is java.security.KeyStoreException -> "KeyStore hardware error: ${e.message}"
                e is EncryptionException -> e.message ?: "Security operation failed"
                else -> e.message ?: "Encryption/Decryption failed (${e::class.java.simpleName})"
            }
            Result.Error(message, e)
        }
    }
}
