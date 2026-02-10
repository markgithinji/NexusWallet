package com.example.nexuswallet.feature.wallet.ethereum

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger


class EthereumTransactionRepository(
    private val localDataSource: TransactionLocalDataSource,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
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
            val feeEstimate = ethereumBlockchainRepository.getBitcoinFeeEstimates()
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
        Log.d("TxRepo", " createEthereumTransaction START (LOCAL ONLY)")
        Log.d("TxRepo", "Wallet: ${wallet.address}, Network: ${wallet.network}")
        Log.d("TxRepo", "Amount: $amount ETH to $toAddress")

        return try {
            // 1. Get nonce for display
            Log.d("TxRepo", "Getting nonce from API...")
            val nonce = ethereumBlockchainRepository.getEthereumNonce(wallet.address, wallet.network)
            Log.d("TxRepo", "API returned nonce: $nonce")

            if (nonce == 0 && wallet.network == EthereumNetwork.SEPOLIA) {
                Log.d("TxRepo", " Nonce 0 is correct for new wallet on Sepolia")
            }

            Log.d("TxRepo", "Display nonce: $nonce")

            // 2. Get gas price for display
            val gasPrice = ethereumBlockchainRepository.getEthereumGasPrice(wallet.network)
            val selectedFee = when (feeLevel) {
                FeeLevel.SLOW -> gasPrice.slow
                FeeLevel.FAST -> gasPrice.fast
                else -> gasPrice.normal
            }
            Log.d("TxRepo", "Display gas price: ${selectedFee.gasPrice} Gwei")

            // 3. Convert amount to wei WITHOUT DECIMAL POINT
            val amountWei = amount.multiply(BigDecimal("1000000000000000000"))
            val amountWeiInt = amountWei.toBigInteger()  // Convert to integer
            Log.d("TxRepo", "Amount in wei (BigInteger): $amountWeiInt")

            // 4. Create LOCAL transaction record ONLY (DO NOT SIGN OR BROADCAST)
            val transaction = SendTransaction(
                id = "tx_${System.currentTimeMillis()}",
                walletId = wallet.id,
                walletType = WalletType.ETHEREUM,
                fromAddress = wallet.address,
                toAddress = toAddress,
                amount = amountWeiInt.toString(),
                amountDecimal = amount.toPlainString(),
                fee = selectedFee.totalFee,
                feeDecimal = selectedFee.totalFeeDecimal,
                total = (amountWeiInt + BigDecimal(selectedFee.totalFee).toBigInteger()).toString(),
                totalDecimal = (amount + BigDecimal(selectedFee.totalFeeDecimal)).toPlainString(),
                chain = if (wallet.network == EthereumNetwork.SEPOLIA) ChainType.ETHEREUM_SEPOLIA else ChainType.ETHEREUM,
                status = TransactionStatus.PENDING,
                note = note,
                gasPrice = selectedFee.gasPrice,
                gasLimit = "21000",
                signedHex = null,
                nonce = nonce,
                hash = null,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel
            )

            // 5. Save to local storage only
            localDataSource.saveSendTransaction(transaction)
            Log.d("TxRepo", " Saved LOCAL transaction (not signed/broadcasted)")

            Result.success(transaction)

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

            Log.d("TxRepo", " Signing transaction: ${transaction.id}")
            Log.d("TxRepo", "Network: ${wallet.network}, Fee Level: ${transaction.feeLevel}")

            // 1. Get CURRENT nonce
            Log.d("TxRepo", "Getting fresh nonce from API...")
            val currentNonce = ethereumBlockchainRepository.getEthereumNonce(wallet.address, wallet.network)
            Log.d("TxRepo", "API returned nonce: $currentNonce")

            // Remove this override
            if (currentNonce == 0 && wallet.network == EthereumNetwork.SEPOLIA) {
                Log.d("TxRepo", " Nonce 0 is correct for new wallet on Sepolia")
            }

            Log.d("TxRepo", "Signing nonce: $currentNonce")

            // 2. Get CURRENT gas price
            val gasPrice = ethereumBlockchainRepository.getCurrentGasPrice(wallet.network)
            val selectedGasPrice = when (transaction.feeLevel ?: FeeLevel.NORMAL) {
                FeeLevel.SLOW -> gasPrice.safe
                FeeLevel.FAST -> gasPrice.fast
                else -> gasPrice.propose
            }
            Log.d("TxRepo", "Signing gas price: $selectedGasPrice Gwei")

            // 3. Convert gas price to hex
            val gasPriceWei = (BigDecimal(selectedGasPrice) * BigDecimal("1000000000")).toBigInteger()
            val gasPriceHex = "0x${gasPriceWei.toString(16)}"
            Log.d("TxRepo", "Gas price hex: $gasPriceHex")

            // 4. Get private key
            Log.d("TxRepo", "Requesting private key...")
            val privateKeyResult = keyManager.getPrivateKeyForSigning(transaction.walletId)

            if (privateKeyResult.isFailure) {
                Log.e("TxRepo", "Failed to get private key")
                return Result.failure(
                    privateKeyResult.exceptionOrNull() ?: IllegalStateException("No private key")
                )
            }

            val privateKey = privateKeyResult.getOrThrow()
            Log.d("TxRepo", "✓ Got private key")

            // 5. Create credentials
            val credentials = Credentials.create(privateKey)
            if (credentials.address.lowercase() != wallet.address.lowercase()) {
                Log.e("TxRepo", "Address mismatch!")
                return Result.failure(IllegalStateException("Private key doesn't match wallet"))
            }

            // 6. Prepare transaction with CURRENT values
            val amountWei = try {
                BigDecimal(transaction.amount).toBigInteger()
            } catch (e: Exception) {
                Log.e("TxRepo", "Error parsing amount: ${transaction.amount}", e)
                return Result.failure(IllegalArgumentException("Invalid amount format"))
            }

            Log.d("TxRepo", "Amount wei (BigInteger): $amountWei")

            val valueHex = "0x" + amountWei.toString(16)
            Log.d("TxRepo", "Value hex: $valueHex")

            Log.d("TxRepo", "Creating raw transaction...")
            Log.d("TxRepo", "Nonce: $currentNonce, Gas Price: $gasPriceWei, To: ${transaction.toAddress}, Value: $amountWei")

            val rawTransaction = RawTransaction.createTransaction(
                BigInteger.valueOf(currentNonce.toLong()),
                gasPriceWei,
                BigInteger("21000"),
                transaction.toAddress,
                amountWei,
                ""
            )

            // 7. Sign with current chain ID
            val chainId = if (wallet.network == EthereumNetwork.SEPOLIA) 11155111L else 1L
            Log.d("TxRepo", "Signing with chain ID: $chainId")

            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
            val signedHex = Numeric.toHexString(signedMessage)

            // 8. Calculate hash
            val txHashBytes = Hash.sha3(Numeric.hexStringToByteArray(signedHex))
            val calculatedHash = Numeric.toHexString(txHashBytes)

            Log.d("TxRepo", "Signed! Hash: ${calculatedHash.take(16)}...")

            // 9. Create signed transaction
            val signedTransaction = SignedTransaction(
                rawHex = signedHex,
                hash = calculatedHash,
                chain = transaction.chain
            )

            // 10. Update transaction with signed data
            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                hash = calculatedHash,
                signedHex = signedHex,
                nonce = currentNonce,
                gasPrice = selectedGasPrice,
                gasLimit = "21000"
            )
            localDataSource.saveSendTransaction(updatedTransaction)

            Result.success(signedTransaction)

        } catch (e: Exception) {
            Log.e("TxRepo", " Signing failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun broadcastTransactionReal(transactionId: String): Result<BroadcastResult> {
        Log.d("BroadcastDebug", "🎬 START broadcastTransactionReal")
        Log.d("BroadcastDebug", "Transaction ID: $transactionId")

        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: run {
                    Log.e("BroadcastDebug", " Transaction not found")
                    return Result.failure(IllegalArgumentException("Transaction not found"))
                }

            Log.d("BroadcastDebug", "Found transaction: ${transaction.id}")
            Log.d("BroadcastDebug", "Signed hex available: ${transaction.signedHex != null}")
            Log.d("BroadcastDebug", "Signed hex length: ${transaction.signedHex?.length}")
            Log.d("BroadcastDebug", "Transaction hash: ${transaction.hash}")

            // 1. Check if transaction is signed
            val signedHex = transaction.signedHex
                ?: run {
                    Log.e("BroadcastDebug", " Transaction not signed")
                    return Result.failure(IllegalStateException("Transaction not signed"))
                }

            // 2. Get wallet for network info
            val wallet = walletRepository.getWallet(transaction.walletId) as? EthereumWallet
                ?: run {
                    Log.e("BroadcastDebug", " Ethereum wallet not found")
                    return Result.failure(IllegalArgumentException("Wallet not found"))
                }

            Log.d("BroadcastDebug", " Broadcasting transaction...")
            Log.d("BroadcastDebug", "Network: ${wallet.network}")
            Log.d("BroadcastDebug", "Signed TX (first 100): ${signedHex.take(100)}...")
            Log.d("BroadcastDebug", "Chain ID: ${if (wallet.network == EthereumNetwork.SEPOLIA) "11155111" else "1"}")

            // 3. Broadcast
            Log.d("BroadcastDebug", "Calling blockchainRepository.broadcastEthereumTransaction...")
            val broadcastResult = ethereumBlockchainRepository.broadcastEthereumTransaction(
                signedHex,
                wallet.network
            )

            Log.d("BroadcastDebug", "Broadcast result: success=${broadcastResult.success}")
            Log.d("BroadcastDebug", "Broadcast hash: ${broadcastResult.hash}")
            Log.d("BroadcastDebug", "Broadcast error: ${broadcastResult.error}")

            // 4. Update transaction status
            val updatedTransaction = if (broadcastResult.success) {
                Log.d("BroadcastDebug", " Transaction broadcast successful!")
                transaction.copy(
                    status = TransactionStatus.SUCCESS,
                    hash = broadcastResult.hash ?: transaction.hash
                )
            } else {
                Log.e("BroadcastDebug", " Transaction broadcast failed: ${broadcastResult.error}")
                transaction.copy(
                    status = TransactionStatus.FAILED
                )
            }
            localDataSource.saveSendTransaction(updatedTransaction)

            Result.success(broadcastResult)

        } catch (e: Exception) {
            Log.e("BroadcastDebug", " Broadcast error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Updated broadcastTransaction method with real implementation
     */
    suspend fun broadcastTransaction(transactionId: String): Result<BroadcastResult> {
        Log.d("TxRepo", " ENTERING broadcastTransaction for: $transactionId")

        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: run {
                    Log.e("TxRepo", " Transaction not found")
                    return Result.failure(IllegalArgumentException("Transaction not found"))
                }

            Log.d("TxRepo", "Transaction found: ${transaction.id}, chain: ${transaction.chain}")

            // Only handle Ethereum Sepolia for now
            when (transaction.chain) {
                ChainType.ETHEREUM_SEPOLIA -> {
                    Log.d("TxRepo", " Using real broadcast for Sepolia")
                    broadcastTransactionReal(transactionId)
                }
                else -> {
                    Log.w("TxRepo", "⚠ Chain ${transaction.chain} not supported for broadcasting")
                    // Return mock result for unsupported chains
                    Result.success(
                        BroadcastResult(
                            success = false,
                            error = "Chain ${transaction.chain} broadcasting not implemented",
                            chain = transaction.chain
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TxRepo", " Broadcast failed: ${e.message}", e)
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

sealed class TransactionState {
    data object Idle : TransactionState()
    data object Loading : TransactionState()
    data class Created(val transaction: SendTransaction) : TransactionState()
    data class Success(val hash: String) : TransactionState()
    data class Error(val message: String) : TransactionState()
}