package com.example.nexuswallet.feature.coin.bitcoin

import android.util.Log
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.ui.SendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.DumpedPrivateKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Utils
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

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
            Log.d("SendBitcoinUC", "========== SEND BITCOIN START ==========")
            Log.d("SendBitcoinUC", "WalletId: $walletId, To: $toAddress, Amount: $amount BTC")

            // 1. Create transaction
            Log.d("SendBitcoinUC", "Step 1: Creating transaction...")
            val transaction = createTransaction(walletId, toAddress, amount, feeLevel, note)
                ?: return@withContext Result.Error("Failed to create transaction", null)

            Log.d("SendBitcoinUC", " Transaction created: ${transaction.id}")

            // 2. Sign transaction
            Log.d("SendBitcoinUC", "Step 2: Signing transaction...")
            val signedTransaction = signTransaction(transaction)
                ?: return@withContext Result.Error("Failed to sign transaction", null)

            Log.d("SendBitcoinUC", " Transaction signed: ${signedTransaction.txHash}")

            // 3. Broadcast transaction
            Log.d("SendBitcoinUC", "Step 3: Broadcasting transaction...")
            val broadcastResult = broadcastTransaction(signedTransaction)

            Log.d(
                "SendBitcoinUC",
                " Broadcast result: success=${broadcastResult.success}, hash=${broadcastResult.hash}"
            )

            val sendResult = SendBitcoinResult(
                transactionId = transaction.id,
                txHash = broadcastResult.hash ?: signedTransaction.txHash ?: "",
                success = broadcastResult.success,
                error = broadcastResult.error
            )

            Log.d("SendBitcoinUC", "========== SEND COMPLETE ==========")
            Result.Success(sendResult)

        } catch (e: Exception) {
            Log.e("SendBitcoinUC", " Exception: ${e.message}", e)
            Result.Error("Send failed: ${e.message}", e)
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
            val wallet = walletRepository.getWallet(walletId)
                ?: run {
                    Log.e("SendBitcoinUC", "Wallet not found: $walletId")
                    return null
                }

            val bitcoinCoin = wallet.bitcoin
                ?: run {
                    Log.e("SendBitcoinUC", "Bitcoin not enabled for wallet: $walletId")
                    return null
                }

            Log.d("SendBitcoinUC", "Creating transaction for address: ${bitcoinCoin.address}")

            val feeResult = bitcoinBlockchainRepository.getFeeEstimate(feeLevel)
            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                is Result.Error -> {
                    Log.e("SendBitcoinUC", "Failed to get fee estimate: ${feeResult.message}")
                    return null
                }
                Result.Loading -> {
                    Log.e("SendBitcoinUC", "Timeout getting fee estimate")
                    return null
                }
            }

            val satoshis = amount.multiply(BigDecimal("100000000")).toLong()

            val fee = feeEstimate.totalFeeSatoshis

            val transaction = BitcoinTransaction(
                id = "btc_tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = bitcoinCoin.address,
                toAddress = toAddress,
                amountSatoshis = satoshis,
                amountBtc = amount.toPlainString(),
                feeSatoshis = fee,
                feeBtc = feeEstimate.totalFeeBtc,
                feePerByte = feeEstimate.feePerByte,
                estimatedSize = feeEstimate.estimatedSize,
                signedHex = null,
                txHash = null,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = bitcoinCoin.network.name
            )

            bitcoinTransactionRepository.saveTransaction(transaction)
            Log.d("SendBitcoinUC", " Transaction saved locally: ${transaction.id}")
            return transaction

        } catch (e: Exception) {
            Log.e("SendBitcoinUC", "Error creating transaction: ${e.message}", e)
            return null
        }
    }

    private suspend fun signTransaction(transaction: BitcoinTransaction): BitcoinTransaction? {
        try {
            Log.d("SendBitcoinUC", "Signing transaction: ${transaction.id}")

            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: run {
                    Log.e("SendBitcoinUC", "Wallet not found: ${transaction.walletId}")
                    return null
                }

            val bitcoinCoin = wallet.bitcoin
                ?: run {
                    Log.e(
                        "SendBitcoinUC",
                        "Bitcoin not enabled for wallet: ${transaction.walletId}"
                    )
                    return null
                }

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

            val ecKey = try {
                DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key
            } catch (e: Exception) {
                Log.e("SendBitcoinUC", "Invalid private key format: ${e.message}")
                return null
            }

            val derivedAddress = LegacyAddress.fromKey(networkParams, ecKey).toString()
            if (derivedAddress != bitcoinCoin.address) {
                Log.e("SendBitcoinUC", "Private key doesn't match wallet address")
                return null
            }

            val signResult = bitcoinBlockchainRepository.createAndSignTransaction(
                fromKey = ecKey,
                toAddress = transaction.toAddress,
                satoshis = transaction.amountSatoshis,
                network = bitcoinCoin.network
            )

            val signedTx = when (signResult) {
                is Result.Success -> signResult.data
                is Result.Error -> {
                    Log.e("SendBitcoinUC", "Signing failed: ${signResult.message}")
                    return null
                }

                Result.Loading -> {
                    Log.e("SendBitcoinUC", "Signing timeout")
                    return null
                }
            }

            val txHash = signedTx.txId.toString()
            val signedHex = Utils.HEX.encode(signedTx.bitcoinSerialize())

            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                txHash = txHash,
                signedHex = signedHex
            )

            bitcoinTransactionRepository.updateTransaction(updatedTransaction)
            Log.d("SendBitcoinUC", " Transaction signed: $txHash")
            return updatedTransaction

        } catch (e: Exception) {
            Log.e("SendBitcoinUC", "Error signing transaction: ${e.message}", e)
            return null
        }
    }

    private suspend fun broadcastTransaction(transaction: BitcoinTransaction): BroadcastResult {
        try {
            if (transaction.signedHex == null) {
                Log.e("SendBitcoinUC", "Transaction not signed")
                return BroadcastResult(success = false, error = "Transaction not signed")
            }

            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: return BroadcastResult(success = false, error = "Wallet not found")

            val bitcoinCoin = wallet.bitcoin
                ?: return BroadcastResult(success = false, error = "Bitcoin not enabled")

            val broadcastResult = bitcoinBlockchainRepository.broadcastTransaction(
                signedHex = transaction.signedHex,
                network = bitcoinCoin.network
            )

            return when (broadcastResult) {
                is Result.Success -> {
                    val result = broadcastResult.data
                    val updatedTransaction = if (result.success) {
                        transaction.copy(
                            status = TransactionStatus.SUCCESS,
                            txHash = result.hash ?: transaction.txHash
                        )
                    } else {
                        transaction.copy(
                            status = TransactionStatus.FAILED
                        )
                    }
                    bitcoinTransactionRepository.updateTransaction(updatedTransaction)

                    Log.d("SendBitcoinUC", " Broadcast result: ${result.success}")
                    result
                }

                is Result.Error -> {
                    Log.e("SendBitcoinUC", "Broadcast failed: ${broadcastResult.message}")
                    BroadcastResult(success = false, error = broadcastResult.message)
                }

                Result.Loading -> {
                    Log.e("SendBitcoinUC", "Broadcast timeout")
                    BroadcastResult(success = false, error = "Broadcast timeout")
                }
            }

        } catch (e: Exception) {
            Log.e("SendBitcoinUC", "Error broadcasting: ${e.message}", e)
            return BroadcastResult(success = false, error = e.message ?: "Broadcast failed")
        }
    }
}

data class SendBitcoinResult(
    override val transactionId: String,
    override val txHash: String,
    override val success: Boolean,
    override val error: String? = null
) : SendResult

@Singleton
class GetBitcoinFeeEstimateUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository
) {
    suspend operator fun invoke(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<BitcoinFeeEstimate> {
        return bitcoinBlockchainRepository.getFeeEstimate(feeLevel)
    }
}

@Singleton
class GetBitcoinBalanceUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository
) {
    suspend operator fun invoke(address: String, network: BitcoinNetwork): Result<BigDecimal> {
        return bitcoinBlockchainRepository.getBalance(address, network)
    }
}

@Singleton
class ValidateBitcoinAddressUseCase @Inject constructor() {
    operator fun invoke(address: String, network: BitcoinNetwork): Boolean {
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