package com.example.nexuswallet.feature.core.data.util

import java.security.KeyStoreException

inline fun <T> safeKeyStoreCall(block: () -> T): T {
    return try {
        block()
    } catch (e: Exception) {
        val isAuthRequired = e is android.security.keystore.UserNotAuthenticatedException ||
                e.cause is android.security.keystore.UserNotAuthenticatedException ||
                (e is javax.crypto.IllegalBlockSizeException && e.message?.contains("user not authenticated", true) == true) ||
                (e.cause?.message?.contains("user not authenticated", true) == true)

        if (isAuthRequired) {
            // Rethrow the specific auth exception so UseCases can trigger biometrics
            throw (e as? android.security.keystore.UserNotAuthenticatedException) 
                ?: (e.cause as? android.security.keystore.UserNotAuthenticatedException)
                ?: android.security.keystore.UserNotAuthenticatedException()
        }

        // For all other actual errors, wrap in our custom exception
        if (e is java.security.KeyStoreException) {
            throw EncryptionException("KeyStore hardware error", e)
        }
        throw EncryptionException("Encryption/Decryption failed", e)
    }
}
