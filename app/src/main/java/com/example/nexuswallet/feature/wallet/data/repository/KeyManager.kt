package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.feature.authentication.domain.EncryptionResult
import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyManager @Inject constructor(
    private val securityManager: SecurityManager
) {
    /**
     * Get private key for signing
     */
    suspend fun getPrivateKeyForSigning(
        walletId: String,
        keyType: String = "ETH_PRIVATE_KEY"
    ): Result<String> {
        return try {
            // Get private key from SecurityManager with key type
            val privateKeyResult = securityManager.getPrivateKeyForSigning(
                walletId = walletId,
                keyType = keyType,
                requireAuth = false
            )

            if (privateKeyResult.isFailure) {
                val error = privateKeyResult.exceptionOrNull()
                return Result.failure(
                    error
                        ?: IllegalStateException("Failed to retrieve private key for type: $keyType")
                )
            }

            val privateKey = privateKeyResult.getOrThrow()
            Result.success(privateKey)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Store private key during wallet creation
     */
    suspend fun storePrivateKey(
        walletId: String,
        privateKey: String,
        keyType: String = "ETH_PRIVATE_KEY"
    ): Result<Unit> {
        return try {
            // Validate based on key type
            val isValid = when (keyType) {
                "ETH_PRIVATE_KEY" -> isValidEthereumPrivateKey(privateKey)
                "SOLANA_PRIVATE_KEY" -> isValidSolanaPrivateKey(privateKey)
                "BTC_PRIVATE_KEY" -> isValidBitcoinPrivateKey(privateKey)
                else -> true
            }

            if (!isValid) {
                return Result.failure(IllegalArgumentException("Invalid private key format"))
            }

            val result = securityManager.encryptAndStorePrivateKey(
                walletId = walletId,
                privateKey = privateKey,
                keyType = keyType
            )

            when (result) {
                is EncryptionResult.Success -> Result.success(Unit)
                is EncryptionResult.Error -> Result.failure(result.exception)
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validate Bitcoin private key format
     */
    fun isValidBitcoinPrivateKey(privateKey: String): Boolean {
        return try {
            // Bitcoin private keys are typically WIF format
            // WIF format starts with specific characters based on network
            when {
                // Mainnet uncompressed private key WIF
                privateKey.startsWith("5") && privateKey.length in 51..52 -> true
                // Mainnet compressed private key WIF
                privateKey.startsWith("L") || privateKey.startsWith("K") -> true
                // Testnet private key WIF
                privateKey.startsWith("9") || privateKey.startsWith("c") -> true
                // Could also be raw hex (64 characters)
                privateKey.length == 64 && privateKey.all { it in "0123456789abcdefABCDEF" } -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate Ethereum private key format
     */
    fun isValidEthereumPrivateKey(privateKey: String): Boolean {
        return try {
            // Remove 0x prefix if present
            val key = if (privateKey.startsWith("0x")) {
                privateKey.substring(2)
            } else {
                privateKey
            }

            // Should be 64 hex characters (32 bytes)
            if (key.length != 64) {
                return false
            }

            // Should be valid hex
            key.toBigInteger(16)
            true

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate Solana private key format
     */
    fun isValidSolanaPrivateKey(privateKey: String): Boolean {
        return try {
            // Remove 0x prefix if present
            val key = if (privateKey.startsWith("0x")) {
                privateKey.substring(2)
            } else {
                privateKey
            }

            // Solana private key from sol4k is 128 hex chars (64 bytes)
            // This is the full keypair (32-byte seed + 32-byte public key)
            if (key.length != 128) {
                // Also accept 64 hex chars (32 bytes seed only)
                if (key.length == 64) {
                    // Validate it's valid hex
                    key.toBigInteger(16)
                    return true
                }
                return false
            }

            // Should be valid hex
            key.toBigInteger(16)
            true

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear private key from memory (security best practice)
     */
    private fun clearKeyFromMemory(key: String) {
        try {
            val chars = key.toCharArray()
            chars.fill('0')
        } catch (e: Exception) {
            // Silently handle failure to clear
        }
    }
}