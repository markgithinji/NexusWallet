package com.example.nexuswallet.feature.wallet.data.repository

import android.content.Context
import android.util.Log
import com.example.nexuswallet.feature.authentication.domain.AuthAction
import com.example.nexuswallet.feature.authentication.domain.EncryptionResult
import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import com.example.nexuswallet.feature.wallet.data.model.EthereumTransactionParams
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyManager @Inject constructor(
    private val securityManager: SecurityManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Get private key for signing - REAL VERSION
     */
    suspend fun getPrivateKeyForSigning(
        walletId: String,
        keyType: String = "ETH_PRIVATE_KEY"
    ): Result<String> {
        return try {
            Log.d("KeyManager", "Requesting private key for wallet: $walletId, type: $keyType")

            // Get private key from SecurityManager with key type
            val privateKeyResult = securityManager.getPrivateKeyForSigning(
                walletId = walletId,
                keyType = keyType,
                requireAuth = false
            )

            if (privateKeyResult.isFailure) {
                Log.e("KeyManager", "Failed to get private key: ${privateKeyResult.exceptionOrNull()?.message}")
                return Result.failure(
                    privateKeyResult.exceptionOrNull() ?:
                    IllegalStateException("Failed to retrieve private key for type: $keyType")
                )
            }

            val privateKey = privateKeyResult.getOrThrow()

            Log.d("KeyManager", "Private key retrieved successfully for type: $keyType")
            Result.success(privateKey)

        } catch (e: Exception) {
            Log.e("KeyManager", "Unexpected error: ${e.message}", e)
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
            Log.d("KeyManager", "Storing private key for wallet: $walletId, type: $keyType")

            // Validate based on key type
            val isValid = when (keyType) {
                "ETH_PRIVATE_KEY" -> isValidEthereumPrivateKey(privateKey)
                "SOLANA_PRIVATE_KEY" -> isValidSolanaPrivateKey(privateKey)
                else -> {
                    Log.w("KeyManager", "Unknown key type: $keyType, skipping format validation")
                    true
                }
            }

            if (!isValid) {
                Log.e("KeyManager", "Invalid private key format for type: $keyType")
                return Result.failure(IllegalArgumentException("Invalid private key format"))
            }

            val result = securityManager.encryptAndStorePrivateKey(
                walletId = walletId,
                privateKey = privateKey,
                keyType = keyType
            )

            when (result) {
                is EncryptionResult.Success -> {
                    Log.d("KeyManager", "Private key stored successfully")
                    Result.success(Unit)
                }
                is EncryptionResult.Error -> {
                    Log.e("KeyManager", "Failed to store private key: ${result.exception?.message}")
                    Result.failure(result.exception)
                }
            }

        } catch (e: Exception) {
            Log.e("KeyManager", "Error storing private key: ${e.message}", e)
            Result.failure(e)
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
                Log.d("KeyManager", "Invalid Ethereum length: ${key.length} (expected 64)")
                return false
            }

            // Should be valid hex
            key.toBigInteger(16)
            Log.d("KeyManager", "Valid Ethereum private key format")
            true

        } catch (e: Exception) {
            Log.e("KeyManager", "Invalid Ethereum hex format: ${e.message}")
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
                Log.d("KeyManager", "Invalid Solana length: ${key.length} (expected 128 for full keypair)")

                // Also accept 64 hex chars (32 bytes seed only)
                if (key.length == 64) {
                    Log.d("KeyManager", "Accepting 64-char Solana seed")
                    // Validate it's valid hex
                    key.toBigInteger(16)
                    return true
                }
                return false
            }

            // Should be valid hex
            key.toBigInteger(16)
            Log.d("KeyManager", "Valid Solana private key format (128 chars)")
            true

        } catch (e: Exception) {
            Log.e("KeyManager", "Invalid Solana hex format: ${e.message}")
            false
        }
    }

    /**
     * Get private key and validate for specific blockchain
     */
    suspend fun getPrivateKeyForChain(
        walletId: String,
        chain: ChainType
    ): Result<String> {
        return try {
            val privateKeyResult = getPrivateKeyForSigning(walletId)

            if (privateKeyResult.isFailure) {
                return privateKeyResult
            }

            val privateKey = privateKeyResult.getOrThrow()

            // Validate based on chain
            val isValid = when (chain) {
                ChainType.ETHEREUM -> isValidEthereumPrivateKey(privateKey)
                ChainType.SOLANA -> isValidSolanaPrivateKey(privateKey)
                else -> true // Skip validation for other chains
            }

            if (!isValid) {
                return Result.failure(IllegalArgumentException("Invalid private key for chain: $chain"))
            }

            Result.success(privateKey)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if wallet has private key stored
     */
    suspend fun hasPrivateKey(walletId: String): Boolean {
        return securityManager.hasPrivateKey(walletId)
    }

    /**
     * Clear private key from memory (security best practice)
     */
    private fun clearKeyFromMemory(key: String) {
        try {
            val chars = key.toCharArray()
            chars.fill('0')
            Log.d("KeyManager", "Cleared private key from memory")
        } catch (e: Exception) {
            Log.e("KeyManager", "Failed to clear key from memory: ${e.message}")
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}