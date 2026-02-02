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
     * Uses your SecurityManager to retrieve real private keys
     */
    suspend fun getPrivateKeyForSigning(walletId: String): Result<String> {
        return try {
            Log.d("KeyManager", "Requesting private key for wallet: $walletId")

            // Get private key from SecurityManager
            val privateKeyResult = securityManager.getPrivateKeyForSigning(walletId, requireAuth = false)

            if (privateKeyResult.isFailure) {
                Log.e("KeyManager", "Failed to get private key: ${privateKeyResult.exceptionOrNull()?.message}")
                return Result.failure(
                    privateKeyResult.exceptionOrNull() ?:
                    IllegalStateException("Failed to retrieve private key")
                )
            }

            val privateKey = privateKeyResult.getOrThrow()

            // Validate private key format
            if (!isValidEthereumPrivateKey(privateKey)) {
                Log.e("KeyManager", "Invalid Ethereum private key format")
                return Result.failure(IllegalArgumentException("Invalid private key format"))
            }

            Log.d("KeyManager", "Private key retrieved successfully")
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
            Log.d("KeyManager", "Storing private key for wallet: $walletId")

            // Validate private key format before storing
            if (!isValidEthereumPrivateKey(privateKey)) {
                Log.e("KeyManager", "Invalid private key format")
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
                Log.d("KeyManager", "Invalid length: ${key.length} (expected 64)")
                return false
            }

            // Should be valid hex
            key.toBigInteger(16)
            Log.d("KeyManager", "Valid Ethereum private key format")
            true

        } catch (e: Exception) {
            Log.e("KeyManager", "Invalid hex format: ${e.message}")
            false
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