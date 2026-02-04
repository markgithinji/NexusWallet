package com.example.nexuswallet.feature.wallet.data.repository

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.EthereumTransactionParams
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.model.ValidationResult
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger


class TransactionRepository(
    private val localDataSource: TransactionLocalDataSource,
    private val blockchainRepository: BlockchainRepository,
    private val walletRepository: WalletRepository,
    private val keyManager: KeyManager
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun createSendTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendTransaction> {
        return try {
            val wallet = walletRepository.getWallet(walletId) ?: return Result.failure(
                IllegalArgumentException("Wallet not found")
            )

            when (wallet) {
                is BitcoinWallet -> createBitcoinTransaction(
                    wallet = wallet,
                    toAddress = toAddress,
                    amount = amount,
                    feeLevel = feeLevel,
                    note = note
                )

                is EthereumWallet -> createEthereumTransaction(
                    wallet = wallet,
                    toAddress = toAddress,
                    amount = amount,
                    feeLevel = feeLevel,
                    note = note
                )

                else -> Result.failure(IllegalArgumentException("Unsupported wallet type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createBitcoinTransaction(
        wallet: BitcoinWallet,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SendTransaction> {
        return try {
            // Convert amount to satoshis
            val amountSat = amount.multiply(BigDecimal("100000000")).toLong()

            // Get fee estimate
            val feeEstimate = blockchainRepository.getBitcoinFeeEstimates()
            val selectedFee = when (feeLevel) {
                FeeLevel.SLOW -> feeEstimate.slow
                FeeLevel.FAST -> feeEstimate.fast
                else -> feeEstimate.normal
            }

            // For demo, use simple fee calculation
            val feeSat = selectedFee.totalFee.toLong()

            // Create transaction
            val transaction = SendTransaction(
                id = "tx_${System.currentTimeMillis()}",
                walletId = wallet.id,
                walletType = WalletType.BITCOIN,
                fromAddress = wallet.address,
                toAddress = toAddress,
                amount = amountSat.toString(),
                amountDecimal = amount.toPlainString(),
                fee = feeSat.toString(),
                feeDecimal = selectedFee.totalFeeDecimal,
                total = (amountSat + feeSat).toString(),
                totalDecimal = (amount + BigDecimal(selectedFee.totalFeeDecimal)).toPlainString(),
                chain = ChainType.BITCOIN,
                status = TransactionStatus.PENDING,
                note = note
            )

            // Save to local storage
            localDataSource.saveSendTransaction(transaction)

            Result.success(transaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createEthereumTransaction(
        wallet: EthereumWallet,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SendTransaction> {
        Log.d("TxRepo", " createEthereumTransaction START")
        Log.d("TxRepo", "Wallet: ${wallet.address}, Network: ${wallet.network}")
        Log.d("TxRepo", "Amount: $amount ETH to $toAddress")

        return try {
            // 1. Get nonce
            val nonce = if (wallet.network == EthereumNetwork.SEPOLIA) {
                Log.d("TxRepo", "Sepolia detected - getting nonce...")
                val apiNonce = blockchainRepository.getEthereumNonce(wallet.address, wallet.network)
                Log.d("TxRepo", "API returned nonce: $apiNonce")

                if (apiNonce == 0) {
                    Log.w("TxRepo", "⚠ API returned 0 for Sepolia, using manual override: 5")
                    5L  // Change this to actual nonce!
                } else {
                    apiNonce.toLong()
                }
            } else {
                blockchainRepository.getEthereumNonce(wallet.address, wallet.network).toLong()
            }
            Log.d("TxRepo", "Using nonce: $nonce")

            // 2. Get gas price
            val gasPrice = blockchainRepository.getEthereumGasPrice(wallet.network)
            val selectedFee = when (feeLevel) {
                FeeLevel.SLOW -> gasPrice.slow
                FeeLevel.FAST -> gasPrice.fast
                else -> gasPrice.normal
            }
            Log.d("TxRepo", "Selected gas price: ${selectedFee.gasPrice} Gwei")

            // Convert amount to wei
            val amountWei = amount.multiply(BigDecimal("1000000000000000000"))
            Log.d("TxRepo", "Amount in wei: $amountWei")

            // 3. GET PRIVATE KEY
            Log.d("TxRepo", "Getting private key...")
            val privateKeyResult = keyManager.getPrivateKeyForSigning(wallet.id)

            if (privateKeyResult.isFailure) {
                Log.e("TxRepo", " No private key: ${privateKeyResult.exceptionOrNull()?.message}")
                return Result.failure(
                    privateKeyResult.exceptionOrNull() ?: Exception("No private key")
                )
            }

            val privateKey = privateKeyResult.getOrThrow()
            Log.d("TxRepo", "✓ Got private key")

            // 4. Prepare transaction
            // Use fixed gas price for Sepolia
            val gasPriceHex = if (wallet.network == EthereumNetwork.SEPOLIA) {
                "0x22ecb25c00"  // Your test constant
            } else {
                val gasPriceWei = BigDecimal(selectedFee.gasPrice ?: "30")
                    .multiply(BigDecimal("1000000000"))
                "0x${gasPriceWei.toBigInteger().toString(16)}"
            }
            Log.d("TxRepo", "Gas price hex: $gasPriceHex")

            val valueHex = "0x" + amountWei.toBigInteger().toString(16)
            Log.d("TxRepo", "Value hex: $valueHex")

            // 5. Create and sign transaction
            val rawTransaction = RawTransaction.createTransaction(
                BigInteger.valueOf(nonce),
                BigInteger(gasPriceHex.removePrefix("0x"), 16),
                BigInteger("21000"),
                toAddress,
                BigInteger(valueHex.removePrefix("0x"), 16),
                ""
            )
            Log.d("TxRepo", "Created raw transaction")

            // Sign it (Sepolia chain ID = 11155111)
            val credentials = Credentials.create(privateKey)
            val chainId = if (wallet.network == EthereumNetwork.SEPOLIA) 11155111L else 1L
            val signedMessage = TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                credentials
            )

            val signedHex = Numeric.toHexString(signedMessage)
            Log.d("TxRepo", "✓ Signed transaction (first 50): ${signedHex.take(50)}...")

            // Calculate transaction hash locally for debugging
            val txHashBytes = Hash.sha3(Numeric.hexStringToByteArray(signedHex))
            val calculatedHash = Numeric.toHexString(txHashBytes)
            Log.d("TxRepo", "Calculated TX Hash: $calculatedHash")

            // 6. Create transaction record with signed data
            val transaction = SendTransaction(
                id = "tx_${System.currentTimeMillis()}",
                walletId = wallet.id,
                walletType = WalletType.ETHEREUM,
                fromAddress = wallet.address,
                toAddress = toAddress,
                amount = amountWei.toPlainString(),
                amountDecimal = amount.toPlainString(),
                fee = selectedFee.totalFee,
                feeDecimal = selectedFee.totalFeeDecimal,
                total = (amountWei + BigDecimal(selectedFee.totalFee)).toPlainString(),
                totalDecimal = (amount + BigDecimal(selectedFee.totalFeeDecimal)).toPlainString(),
                chain = if (wallet.network == EthereumNetwork.SEPOLIA) ChainType.ETHEREUM_SEPOLIA else ChainType.ETHEREUM,
                status = TransactionStatus.PENDING,
                note = note,
                gasPrice = selectedFee.gasPrice,
                gasLimit = "21000",
                signedHex = signedHex,
                nonce = nonce.toInt(),
                hash = calculatedHash
            )

            // 7. Save to local storage
            localDataSource.saveSendTransaction(transaction)
            Log.d("TxRepo", "Saved transaction to local storage")

            // 8. BROADCAST IT
            Log.d("TxRepo", "Broadcasting to network...")
            val broadcastResult = blockchainRepository.broadcastEthereumTransaction(
                signedHex,
                wallet.network
            )

            if (broadcastResult.success) {
                Log.d("TxRepo", " Broadcast successful! Hash: ${broadcastResult.hash}")

                // Update with actual broadcast hash
                val updatedTransaction = transaction.copy(
                    status = TransactionStatus.SUCCESS,
                    hash = broadcastResult.hash
                )
                localDataSource.saveSendTransaction(updatedTransaction)

                Result.success(updatedTransaction)
            } else {
                Log.e("TxRepo", " Broadcast failed: ${broadcastResult.error}")
                val failedTransaction = transaction.copy(
                    status = TransactionStatus.FAILED
                )
                localDataSource.saveSendTransaction(failedTransaction)

                Result.failure(Exception("Broadcast failed: ${broadcastResult.error}"))
            }

        } catch (e: Exception) {
            Log.e("TxRepo", " Error in createEthereumTransaction: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signTransaction(transactionId: String): Result<SignedTransaction> {
        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            // Only support Ethereum for now
            if (transaction.walletType != WalletType.ETHEREUM) {
                return Result.failure(IllegalArgumentException("Only Ethereum signing supported"))
            }

            // Use REAL signing
            signEthereumTransactionReal(transactionId)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real Ethereum signing implementation
     */
    private suspend fun signEthereumTransactionReal(
        transactionId: String
    ): Result<SignedTransaction> {
        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            // Get wallet
            val wallet = walletRepository.getWallet(transaction.walletId) as? EthereumWallet
                ?: return Result.failure(IllegalArgumentException("Ethereum wallet not found"))

            // Get nonce
            val nonce = if (wallet.network == EthereumNetwork.SEPOLIA) {
                Log.d("TransactionRepo", "⚠ Sepolia detected - using manual nonce override")
                5  // ← CHANGE THIS TO ACTUAL NONCE!
            } else {

                blockchainRepository.getEthereumNonce(wallet.address, wallet.network)
            }

            Log.d("TransactionRepo", "Using nonce: $nonce")

            val gasPrice = blockchainRepository.getCurrentGasPrice()
            val selectedGasPrice = when (transaction.feeLevel ?: FeeLevel.NORMAL) {
                FeeLevel.SLOW -> gasPrice.safe
                FeeLevel.FAST -> gasPrice.fast
                else -> gasPrice.propose
            }

            Log.d("TransactionRepo", "Selected gas price: $selectedGasPrice Gwei")

            // Convert gas price from Gwei to wei (hex)
            val gasPriceWei = (BigDecimal(selectedGasPrice) * BigDecimal("1000000000")).toBigInteger()
            val gasPriceHex = "0x${gasPriceWei.toString(16)}"

            // Prepare transaction parameters
            val params = EthereumTransactionParams(
                nonce = "0x${nonce.toString(16)}",
                gasPrice = gasPriceHex,
                gasLimit = transaction.gasLimit?.let { "0x${it}" } ?: "0x5208",
                to = transaction.toAddress,
                value = "0x${BigInteger(transaction.amount).toString(16)}",
                network = wallet.network
            )

            Log.d("TransactionRepo", "Transaction params: $params")

            // Get private key from KeyManager
            Log.d("TransactionRepo", "Requesting private key for signing...")
            val privateKeyResult = keyManager.getPrivateKeyForSigning(transaction.walletId)

            if (privateKeyResult.isFailure) {
                Log.e("TransactionRepo", "Failed to get private key: ${privateKeyResult.exceptionOrNull()?.message}")
                return Result.failure(
                    privateKeyResult.exceptionOrNull() ?:
                    IllegalStateException("Failed to retrieve private key")
                )
            }

            val privateKey = privateKeyResult.getOrThrow()
            Log.d("TransactionRepo", "Private key obtained")

            // Create credentials
            val credentials = Credentials.create(privateKey)
            Log.d("TransactionRepo", "Created credentials for address: ${credentials.address}")

            // Verify address matches
            if (credentials.address.lowercase() != wallet.address.lowercase()) {
                Log.e("TransactionRepo", "Address mismatch! Wallet: ${wallet.address}, Credentials: ${credentials.address}")
                return Result.failure(IllegalStateException("Private key doesn't match wallet address"))
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
                params.getChainId(),
                credentials
            )


            val hexValue = Numeric.toHexString(signedMessage)

            // Generate transaction hash
            val txHash = "0x${Hash.sha3(hexValue).substring(0, 64)}"
            Log.d("TransactionRepo", "Generated transaction hash: $txHash")

            val signedTransaction = SignedTransaction(
                rawHex = hexValue,
                hash = txHash,
                chain = ChainType.ETHEREUM
            )

            // Update transaction with real data
            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                hash = txHash,
                signedHex = hexValue,
                nonce = nonce,
                gasPrice = selectedGasPrice,
                gasLimit = params.gasLimit.removePrefix("0x")
            )

            localDataSource.saveSendTransaction(updatedTransaction)

            Log.d("TransactionRepo", "Transaction signed successfully!")
            Result.success(signedTransaction)

        } catch (e: Exception) {
            Log.e("TransactionRepo", "Real ETH signing failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun broadcastTransactionReal(transactionId: String): Result<BroadcastResult> {
        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            // Only support Ethereum for now
            if (transaction.chain != ChainType.ETHEREUM) {
                return Result.failure(IllegalArgumentException("Only Ethereum broadcasting supported"))
            }

            // Get the signed transaction hex
            val signedHex = transaction.signedHex
                ?: return Result.failure(IllegalStateException("Transaction not signed"))

            Log.d("TransactionRepo", "Broadcasting transaction to Ethereum network...")
            Log.d("TransactionRepo", "Signed TX (first 50 chars): ${signedHex.take(50)}...")

            val broadcastResult = blockchainRepository.broadcastEthereumTransaction(signedHex)

            if (broadcastResult.success) {
                Log.d("TransactionRepo", " Broadcast successful! Hash: ${broadcastResult.hash}")

                // Update transaction with real hash
                val updatedTransaction = transaction.copy(
                    status = TransactionStatus.SUCCESS,
                    hash = broadcastResult.hash
                )
                localDataSource.saveSendTransaction(updatedTransaction)

                Result.success(broadcastResult)
            } else {
                Log.e("TransactionRepo", " Broadcast failed: ${broadcastResult.error}")

                // Update transaction as failed
                val failedTransaction = transaction.copy(
                    status = TransactionStatus.FAILED
                )
                localDataSource.saveSendTransaction(failedTransaction)

                Result.failure(IllegalStateException(broadcastResult.error))
            }

        } catch (e: Exception) {
            Log.e("TransactionRepo", "Broadcast error: ${e.message}", e)

            // Update transaction as failed
            val transaction = localDataSource.getSendTransaction(transactionId)
            if (transaction != null) {
                val failedTransaction = transaction.copy(
                    status = TransactionStatus.FAILED
                )
                localDataSource.saveSendTransaction(failedTransaction)
            }

            Result.failure(e)
        }
    }

    /**
     * Updated broadcastTransaction method with real implementation
     */
    suspend fun broadcastTransaction(transactionId: String): Result<BroadcastResult> {
        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            // Use real broadcasting for Ethereum, mock for others
            when (transaction.chain) {
                ChainType.ETHEREUM -> broadcastTransactionReal(transactionId)
                ChainType.BITCOIN -> {

                    broadcastTransactionMock(transactionId)
                }
                else -> broadcastTransactionMock(transactionId)
            }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "Broadcast failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Mock for fallback/testing
     */
    private suspend fun broadcastTransactionMock(transactionId: String): Result<BroadcastResult> {
        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            // Simulate network delay
            delay(1000)

            // Mock successful broadcast
            val broadcastResult = BroadcastResult(
                success = true,
                hash = transaction.hash ?: "mock_broadcast_${System.currentTimeMillis()}",
                chain = transaction.chain
            )

            // Update transaction status
            val updatedTransaction = transaction.copy(
                status = TransactionStatus.SUCCESS
            )
            localDataSource.saveSendTransaction(updatedTransaction)

            Result.success(broadcastResult)
        } catch (e: Exception) {
            // Simulate failure
            val transaction = localDataSource.getSendTransaction(transactionId)
            if (transaction != null) {
                val failedTransaction = transaction.copy(
                    status = TransactionStatus.FAILED
                )
                localDataSource.saveSendTransaction(failedTransaction)
            }

            Result.failure(e)
        }
    }

    // VALIDATION
    suspend fun validateAddress(address: String, chain: ChainType): Boolean {
        return when (chain) {
            ChainType.BITCOIN -> address.startsWith("1") || address.startsWith("3") || address.startsWith("bc1")
            ChainType.ETHEREUM -> address.startsWith("0x") && address.length == 42
            else -> true
        }
    }

    // FEE ESTIMATES
    suspend fun getFeeEstimate(chain: ChainType, feeLevel: FeeLevel): FeeEstimate {
        return when (chain) {
            ChainType.BITCOIN -> when (feeLevel) {
                FeeLevel.SLOW -> FeeEstimate(
                    feePerByte = "10",
                    gasPrice = null,
                    totalFee = "2000",
                    totalFeeDecimal = "0.00002",
                    estimatedTime = 3600,
                    priority = FeeLevel.SLOW
                )
                FeeLevel.FAST -> FeeEstimate(
                    feePerByte = "50",
                    gasPrice = null,
                    totalFee = "10000",
                    totalFeeDecimal = "0.0001",
                    estimatedTime = 120,
                    priority = FeeLevel.FAST
                )
                else -> FeeEstimate(
                    feePerByte = "25",
                    gasPrice = null,
                    totalFee = "5000",
                    totalFeeDecimal = "0.00005",
                    estimatedTime = 600,
                    priority = FeeLevel.NORMAL
                )
            }
            ChainType.ETHEREUM -> when (feeLevel) {
                FeeLevel.SLOW -> FeeEstimate(
                    feePerByte = null,
                    gasPrice = "20",
                    totalFee = "420000000000000",
                    totalFeeDecimal = "0.00042",
                    estimatedTime = 900,
                    priority = FeeLevel.SLOW
                )
                FeeLevel.FAST -> FeeEstimate(
                    feePerByte = null,
                    gasPrice = "50",
                    totalFee = "1050000000000000",
                    totalFeeDecimal = "0.00105",
                    estimatedTime = 60,
                    priority = FeeLevel.FAST
                )
                else -> FeeEstimate(
                    feePerByte = null,
                    gasPrice = "30",
                    totalFee = "630000000000000",
                    totalFeeDecimal = "0.00063",
                    estimatedTime = 300,
                    priority = FeeLevel.NORMAL
                )
            }
            else -> FeeEstimate(
                feePerByte = null,
                gasPrice = null,
                totalFee = "0",
                totalFeeDecimal = "0",
                estimatedTime = 60,
                priority = FeeLevel.NORMAL
            )
        }
    }

    // LOCAL DATA OPERATIONS
    suspend fun getSendTransaction(transactionId: String): SendTransaction? {
        return localDataSource.getSendTransaction(transactionId)
    }
}