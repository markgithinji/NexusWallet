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
class GetBitcoinWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(walletId: String): Result<BitcoinWalletInfo> {
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

        Log.d("GetBitcoinWalletUC", "Loaded wallet: ${wallet.name}, address: ${bitcoinCoin.address.take(8)}...")

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
            Log.d("SendBitcoinUC", "Sending $amount BTC to $toAddress")

            val transaction = createTransaction(walletId, toAddress, amount, feeLevel, note)
                ?: return@withContext Result.Error("Failed to create transaction", null)

            val signedTransaction = signTransaction(transaction)
                ?: return@withContext Result.Error("Failed to sign transaction", null)

            val broadcastResult = broadcastTransaction(signedTransaction)

            val sendResult = SendBitcoinResult(
                transactionId = transaction.id,
                txHash = broadcastResult.hash ?: signedTransaction.txHash ?: "",
                success = broadcastResult.success,
                error = broadcastResult.error
            )

            if (sendResult.success) {
                Log.d("SendBitcoinUC", "Send successful: tx ${sendResult.txHash.take(8)}...")
            } else {
                Log.e("SendBitcoinUC", "Send failed: ${sendResult.error}")
            }

            Result.Success(sendResult)

        } catch (e: Exception) {
            Log.e("SendBitcoinUC", "Send failed: ${e.message}")
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
            val wallet = walletRepository.getWallet(walletId) ?: run {
                Log.e("SendBitcoinUC", "Wallet not found: $walletId")
                return null
            }

            val bitcoinCoin = wallet.bitcoin ?: run {
                Log.e("SendBitcoinUC", "Bitcoin not enabled for wallet: $walletId")
                return null
            }

            val feeResult = bitcoinBlockchainRepository.getFeeEstimate(feeLevel)
            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                else -> {
                    Log.e("SendBitcoinUC", "Failed to get fee estimate")
                    return null
                }
            }

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
                network = bitcoinCoin.network.name
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

            val ecKey = DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key
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
                else -> {
                    Log.e("SendBitcoinUC", "Signing failed")
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
                        transaction.copy(status = TransactionStatus.SUCCESS, txHash = result.hash)
                    } else {
                        transaction.copy(status = TransactionStatus.FAILED)
                    }
                    bitcoinTransactionRepository.updateTransaction(updatedTransaction)

                    if (result.success) {
                        Log.d("SendBitcoinUC", "Broadcast successful: ${result.hash?.take(8)}...")
                    } else {
                        Log.e("SendBitcoinUC", "Broadcast failed: ${result.error}")
                    }
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
            Log.e("SendBitcoinUC", "Error broadcasting: ${e.message}")
            return BroadcastResult(success = false, error = e.message ?: "Broadcast failed")
        }
    }
}

@Singleton
class GetBitcoinFeeEstimateUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository
) {
    suspend operator fun invoke(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<BitcoinFeeEstimate> {
        val result = bitcoinBlockchainRepository.getFeeEstimate(feeLevel)
        if (result is Result.Error) {
            Log.e("GetBitcoinFeeUC", "Failed to get fee estimate: ${result.message}")
        }
        return result
    }
}

@Singleton
class GetBitcoinBalanceUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository
) {
    suspend operator fun invoke(address: String, network: BitcoinNetwork): Result<BigDecimal> {
        val result = bitcoinBlockchainRepository.getBalance(address, network)
        if (result is Result.Success) {
            Log.d("GetBitcoinBalanceUC", "Balance: ${result.data} BTC")
        } else if (result is Result.Error) {
            Log.e("GetBitcoinBalanceUC", "Failed to get balance: ${result.message}")
        }
        return result
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
            Log.d("ValidateBitcoinUC", "Invalid address: $address for ${network.name}")
            false
        }
    }
}