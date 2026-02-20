package com.example.nexuswallet.feature.coin.bitcoin

import android.util.Log
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.DumpedPrivateKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Utils
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncBitcoinTransactionsUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val walletRepository: WalletRepository
) {

    suspend operator fun invoke(walletId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("SyncBitcoinUC", "Syncing Bitcoin transactions for wallet: $walletId")

            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                Log.e("SyncBitcoinUC", "Wallet not found: $walletId")
                return@withContext Result.Error("Wallet not found")
            }

            val bitcoinCoin = wallet.bitcoin
            if (bitcoinCoin == null) {
                Log.e("SyncBitcoinUC", "Bitcoin not enabled for wallet: ${wallet.name}")
                return@withContext Result.Error("Bitcoin not enabled")
            }

            Log.d("SyncBitcoinUC", "Wallet: ${wallet.name}, Address: ${bitcoinCoin.address.take(8)}..., Network: ${bitcoinCoin.network}")

            // Fetch transaction history from blockchain API
            val transactionsResult = bitcoinBlockchainRepository.getAddressTransactions(
                address = bitcoinCoin.address,
                network = bitcoinCoin.network
            )

            when (transactionsResult) {
                is Result.Success -> {
                    val transactions = transactionsResult.data
                    Log.d("SyncBitcoinUC", "Received ${transactions.size} transactions from API")

                    // Clear existing local data before syncing fresh
                    bitcoinTransactionRepository.deleteAllForWallet(walletId)
                    Log.d("SyncBitcoinUC", "Deleted existing transactions")

                    var incoming = 0
                    var outgoing = 0
                    transactions.forEachIndexed { index, tx ->
                        if (tx.isIncoming) incoming++ else outgoing++

                        Log.d("SyncBitcoinUC", "  Transaction #$index: ${tx.txid.take(8)}... | ${if (tx.isIncoming) "IN" else "OUT"} | ${tx.amount} sats")

                        // Convert API response to domain model and save
                        val domainTx = tx.toDomain(
                            walletId = walletId,
                            isIncoming = tx.isIncoming,
                            network = bitcoinCoin.network
                        )
                        bitcoinTransactionRepository.saveTransaction(domainTx)
                    }

                    Log.d("SyncBitcoinUC", "Synced $incoming incoming, $outgoing outgoing transactions")
                    Log.d("SyncBitcoinUC", "Sync completed for wallet $walletId")
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    Log.e("SyncBitcoinUC", "Failed to sync: ${transactionsResult.message}")
                    Result.Error(transactionsResult.message)
                }
                else -> {
                    Log.e("SyncBitcoinUC", "Unknown error during sync")
                    Result.Error("Unknown error")
                }
            }
        } catch (e: Exception) {
            Log.e("SyncBitcoinUC", "Error syncing: ${e.message}")
            Result.Error(e.message ?: "Sync failed")
        }
    }
}

@Singleton
class GetBitcoinWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(walletId: String): Result<BitcoinWalletInfo> {
        Log.d("GetBitcoinWalletUC", "Looking up Bitcoin wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            Log.e("GetBitcoinWalletUC", "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val bitcoinCoin = wallet.bitcoin
        if (bitcoinCoin == null) {
            Log.e("GetBitcoinWalletUC", "Bitcoin not enabled for wallet: ${wallet.name}")
            return Result.Error("Bitcoin not enabled for this wallet")
        }

        Log.d("GetBitcoinWalletUC", "Found wallet: ${wallet.name} | Address: ${bitcoinCoin.address.take(8)}... | Network: ${bitcoinCoin.network}")

        return Result.Success(
            BitcoinWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = bitcoinCoin.address,
                network = bitcoinCoin.network
            )
        )
    }
}

@Singleton
class SendBitcoinUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val keyManager: KeyManager
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendBitcoinResult> = withContext(Dispatchers.IO) {
        try {
            Log.d("SendBitcoinUC", "Sending ${amount.toPlainString()} BTC to ${toAddress.take(8)}...")

            // Step 1: Create and store pending transaction
            val transaction = createTransaction(walletId, toAddress, amount, feeLevel, note)
                ?: return@withContext Result.Error("Failed to create transaction")

            Log.d("SendBitcoinUC", "Transaction created: ${transaction.id}")

            // Step 2: Sign with private key
            val signedTransaction = signTransaction(transaction)
                ?: return@withContext Result.Error("Failed to sign transaction")

            Log.d("SendBitcoinUC", "Transaction signed")

            // Step 3: Broadcast to network
            val txHash = broadcastTransaction(signedTransaction)

            val sendResult = SendBitcoinResult(
                transactionId = transaction.id,
                txHash = txHash ?: "",
                success = txHash != null,
                error = if (txHash == null) "Broadcast failed" else null
            )

            if (sendResult.success) {
                Log.d("SendBitcoinUC", "Send successful: tx ${sendResult.txHash.take(8)}...")
            } else {
                Log.e("SendBitcoinUC", "Send failed: ${sendResult.error}")
            }

            Result.Success(sendResult)

        } catch (e: Exception) {
            Log.e("SendBitcoinUC", "Send failed: ${e.message}")
            Result.Error("Send failed: ${e.message}")
        }
    }

    private suspend fun createTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): BitcoinTransaction? {
        try {
            val wallet = walletRepository.getWallet(walletId) ?: run {
                Log.e("SendBitcoinUC", "Wallet not found: $walletId")
                return null
            }

            val bitcoinCoin = wallet.bitcoin ?: run {
                Log.e("SendBitcoinUC", "Bitcoin not enabled for wallet: $walletId")
                return null
            }

            // Validate address matches network type (mainnet vs testnet)
            if (!validateAddress(toAddress, bitcoinCoin.network)) {
                Log.e("SendBitcoinUC", "Invalid address for ${bitcoinCoin.network}")
                return null
            }

            // Estimate fee based on transaction complexity
            val feeResult = bitcoinBlockchainRepository.getFeeEstimate(
                feeLevel = feeLevel,
                inputCount = 1,
                outputCount = 2 // recipient + change
            )

            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                is Result.Error -> {
                    Log.e("SendBitcoinUC", "Failed to get fee estimate: ${feeResult.message}")
                    return null
                }
                Result.Loading -> {
                    Log.e("SendBitcoinUC", "Fee estimate loading - unexpected")
                    return null
                }
            }

            // Convert BTC amount to satoshis for blockchain operations
            val satoshis = amount.multiply(BigDecimal("100000000")).toLong()

            val transaction = BitcoinTransaction(
                id = "btc_tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = bitcoinCoin.address,
                toAddress = toAddress,
                amountSatoshis = satoshis,
                amountBtc = amount.toPlainString(),
                feeSatoshis = feeEstimate.totalFeeSatoshis,
                feeBtc = feeEstimate.totalFeeBtc,
                feePerByte = feeEstimate.feePerByte,
                estimatedSize = feeEstimate.estimatedSize,
                signedHex = null,
                txHash = null,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = bitcoinCoin.network,
                isIncoming = false
            )

            bitcoinTransactionRepository.saveTransaction(transaction)
            Log.d("SendBitcoinUC", "Transaction created: ${transaction.id}")
            return transaction

        } catch (e: Exception) {
            Log.e("SendBitcoinUC", "Error creating transaction: ${e.message}")
            return null
        }
    }

    private suspend fun signTransaction(transaction: BitcoinTransaction): BitcoinTransaction? {
        try {
            val wallet = walletRepository.getWallet(transaction.walletId) ?: run {
                Log.e("SendBitcoinUC", "Wallet not found: ${transaction.walletId}")
                return null
            }

            val bitcoinCoin = wallet.bitcoin ?: run {
                Log.e("SendBitcoinUC", "Bitcoin not enabled for wallet: ${transaction.walletId}")
                return null
            }

            // Retrieve private key from secure storage
            val privateKeyResult = keyManager.getPrivateKeyForSigning(
                transaction.walletId,
                keyType = "BTC_PRIVATE_KEY"
            )

            if (privateKeyResult.isFailure) {
                Log.e("SendBitcoinUC", "Failed to get private key")
                return null
            }

            val privateKeyWIF = privateKeyResult.getOrThrow()

            val networkParams = when (bitcoinCoin.network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
            }

            // Convert WIF format to ECKey for signing
            val ecKey = DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key
            val derivedAddress = LegacyAddress.fromKey(networkParams, ecKey).toString()

            // Security check: ensure private key matches wallet address
            if (derivedAddress != bitcoinCoin.address) {
                Log.e("SendBitcoinUC", "Private key doesn't match wallet address")
                return null
            }

            // Create and sign the raw transaction
            val signResult = bitcoinBlockchainRepository.createAndSignTransaction(
                fromKey = ecKey,
                toAddress = transaction.toAddress,
                satoshis = transaction.amountSatoshis,
                feeLevel = transaction.feeLevel,
                network = bitcoinCoin.network
            )

            val signedTx = when (signResult) {
                is Result.Success -> signResult.data
                is Result.Error -> {
                    Log.e("SendBitcoinUC", "Signing failed: ${signResult.message}")
                    return null
                }
                Result.Loading -> {
                    Log.e("SendBitcoinUC", "Signing loading - unexpected")
                    return null
                }
            }

            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                txHash = signedTx.txId.toString(),
                signedHex = Utils.HEX.encode(signedTx.bitcoinSerialize())
            )

            bitcoinTransactionRepository.updateTransaction(updatedTransaction)
            Log.d("SendBitcoinUC", "Transaction signed: ${updatedTransaction.txHash}")
            return updatedTransaction

        } catch (e: Exception) {
            Log.e("SendBitcoinUC", "Error signing transaction: ${e.message}")
            return null
        }
    }

    private suspend fun broadcastTransaction(transaction: BitcoinTransaction): String? {
        try {
            if (transaction.signedHex == null) {
                Log.e("SendBitcoinUC", "Transaction not signed")
                return null
            }

            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: return null

            val bitcoinCoin = wallet.bitcoin
                ?: return null

            // Submit signed transaction to the network
            val broadcastResult = bitcoinBlockchainRepository.broadcastTransaction(
                signedHex = transaction.signedHex,
                network = bitcoinCoin.network
            )

            return when (broadcastResult) {
                is Result.Success -> {
                    val txHash = broadcastResult.data
                    val updatedTransaction = transaction.copy(
                        status = TransactionStatus.SUCCESS,
                        txHash = txHash
                    )
                    bitcoinTransactionRepository.updateTransaction(updatedTransaction)
                    Log.d("SendBitcoinUC", "Broadcast successful: ${txHash.take(8)}...")
                    txHash
                }

                is Result.Error -> {
                    val updatedTransaction = transaction.copy(
                        status = TransactionStatus.FAILED
                    )
                    bitcoinTransactionRepository.updateTransaction(updatedTransaction)
                    Log.e("SendBitcoinUC", "Broadcast failed: ${broadcastResult.message}")
                    null
                }

                Result.Loading -> {
                    Log.e("SendBitcoinUC", "Broadcast loading - unexpected")
                    null
                }
            }

        } catch (e: Exception) {
            Log.e("SendBitcoinUC", "Error broadcasting: ${e.message}")
            return null
        }
    }

    private fun validateAddress(address: String, network: BitcoinNetwork): Boolean {
        return try {
            val params = when (network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
            }
            Address.fromString(params, address)
            true
        } catch (e: Exception) {
            false
        }
    }
}

@Singleton
class GetBitcoinFeeEstimateUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository
) {
    suspend operator fun invoke(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        inputCount: Int = 1,
        outputCount: Int = 2
    ): Result<BitcoinFeeEstimate> {
        Log.d("GetBitcoinFeeUC", "Requesting fee estimate: $feeLevel, inputs=$inputCount, outputs=$outputCount")

        val result = bitcoinBlockchainRepository.getFeeEstimate(feeLevel, inputCount, outputCount)

        when (result) {
            is Result.Success -> {
                val fee = result.data
                Log.d("GetBitcoinFeeUC", "Fee estimate: ${fee.feePerByte} sat/byte | Total: ${fee.totalFeeSatoshis} sats (${fee.totalFeeBtc} BTC)")
            }
            is Result.Error -> {
                Log.e("GetBitcoinFeeUC", "Failed to get fee estimate: ${result.message}")
            }

            else -> {}
        }

        return result
    }
}

@Singleton
class GetBitcoinBalanceUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository
) {
    suspend operator fun invoke(address: String, network: BitcoinNetwork): Result<BigDecimal> {
        Log.d("GetBitcoinBalanceUC", "Fetching balance for ${address.take(8)}... on ${network}")

        val result = bitcoinBlockchainRepository.getBalance(address, network)

        when (result) {
            is Result.Success -> {
                Log.d("GetBitcoinBalanceUC", "Balance: ${result.data} BTC")
            }
            is Result.Error -> {
                Log.e("GetBitcoinBalanceUC", "Failed to get balance: ${result.message}")
            }

            else -> {}
        }

        return result
    }
}

@Singleton
class ValidateBitcoinAddressUseCase @Inject constructor() {
    operator fun invoke(address: String, network: BitcoinNetwork): Boolean {
        return try {
            // Attempt to parse address using bitcoinj library
            val params = when (network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
            }
            Address.fromString(params, address)
            Log.d("ValidateBitcoinUC", "Valid ${network} address: ${address.take(8)}...")
            true
        } catch (e: Exception) {
            Log.e("ValidateBitcoinUC", "Invalid ${network} address: ${address.take(8)}...")
            false
        }
    }
}