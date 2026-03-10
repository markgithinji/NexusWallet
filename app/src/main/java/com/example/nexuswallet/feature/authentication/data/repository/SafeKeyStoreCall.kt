package com.example.nexuswallet.feature.authentication.data.repository

import java.security.KeyStoreException

inline fun <T> safeKeyStoreCall(block: () -> T): T {
    return try {
        block()
    } catch (e: KeyStoreException) {
        throw EncryptionException("KeyStore error", e)
    } catch (e: Exception) {
        throw EncryptionException("Encryption failed", e)
    }
}