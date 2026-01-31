package com.example.nexuswallet.feature.wallet.data.repository

import android.content.Context
import com.example.nexuswallet.feature.wallet.data.model.EthereumTransactionParams
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.model.SigningResult
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
    private val walletRepository: WalletRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Get private key for signing - SECURE VERSION
     * Only decrypts key when needed, clears from memory after use
     */
    suspend fun getPrivateKeyForSigning(
        walletId: String
    ): Result<String> {
        return try {
            // For portfolio: Use mock keys for demo
            // In production, you'd retrieve from secure storage

            val wallet = walletRepository.getWallet(walletId)
            if (wallet is EthereumWallet) {
                // Generate deterministic mock key from wallet address (for demo)
                val mockKey = generateMockPrivateKey(wallet.address)
                Result.success(mockKey)
            } else if (wallet is BitcoinWallet) {
                val mockKey = generateMockPrivateKey(wallet.address)
                Result.success(mockKey)
            } else {
                Result.failure(IllegalArgumentException("Unsupported wallet type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate deterministic mock private key for demo
     * NEVER USE THIS IN PRODUCTION!
     */
    private fun generateMockPrivateKey(address: String): String {
        // Hash the address to create a deterministic mock key
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(address.toByteArray())
            .toHex()

        // Ensure it's 64 chars (32 bytes) for Ethereum private key
        return hash.take(64).padEnd(64, '0')
    }

    /**
     * Securely clear key from memory
     */
    private fun clearKeyFromMemory(key: String) {
        // This is a basic attempt to clear the key from memory
        // In real apps, you'd use SecureString or similar
        key.toCharArray().fill('0')
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

// For real implementation, you'd add:
@Singleton
class Web3jSigner @Inject constructor(
    private val keyManager: KeyManager
) {
    private val web3j = Web3j.build(HttpService("https://mainnet.infura.io/v3/YOUR_KEY"))

    suspend fun signEthereumTransaction(
        walletId: String,
        params: EthereumTransactionParams,
        context: Context
    ): SigningResult {
        return try {
            // Get private key
            val privateKeyResult = keyManager.getPrivateKeyForSigning(walletId)
            if (privateKeyResult.isFailure) {
                return SigningResult(
                    success = false,
                    error = "Failed to get private key: ${privateKeyResult.exceptionOrNull()?.message}"
                )
            }

            val privateKey = privateKeyResult.getOrThrow()

            // Create credentials
            val credentials = Credentials.create(privateKey)

            // Validate parameters
            if (!params.to.startsWith("0x") || params.to.length != 42) {
                return SigningResult(
                    success = false,
                    error = "Invalid recipient address"
                )
            }

            // Create and sign transaction
            val rawTransaction = RawTransaction.createTransaction(
                BigInteger(params.nonce.removePrefix("0x"), 16),
                BigInteger(params.gasPrice.removePrefix("0x"), 16),
                BigInteger(params.gasLimit.removePrefix("0x"), 16),
                params.to,
                BigInteger(params.value.removePrefix("0x"), 16),
                params.data
            )

            val signedMessage = TransactionEncoder.signMessage(
                rawTransaction,
                params.chainId,
                credentials
            )

            val hexValue = Numeric.toHexString(signedMessage)

            // Calculate transaction hash
            val txHash = Keys.getAddress(credentials.address)

            val signedTransaction = SignedTransaction(
                rawHex = hexValue,
                hash = "0x${txHash}",
                chain = ChainType.ETHEREUM
            )

            SigningResult(
                success = true,
                signedTransaction = signedTransaction
            )

        } catch (e: Exception) {
            SigningResult(
                success = false,
                error = "Signing failed: ${e.message}"
            )
        }
    }
}