package com.example.nexuswallet.feature.coin.bitcoin

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository

import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.AddressFormatException
import org.bitcoinj.core.Bech32
import org.bitcoinj.core.DumpedPrivateKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Utils
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import org.bitcoinj.core.Address

@Singleton
class SendBitcoinUseCase @Inject constructor(
    private val createBitcoinTransactionUseCase: CreateBitcoinTransactionUseCase,
    private val signBitcoinTransactionUseCase: SignBitcoinTransactionUseCase,
    private val broadcastBitcoinTransactionUseCase: BroadcastBitcoinTransactionUseCase,
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository
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
            val createResult = createBitcoinTransactionUseCase(
                walletId = walletId,
                toAddress = toAddress,
                amount = amount,
                feeLevel = feeLevel,
                note = note
            )

            if (createResult is Result.Error) {
                Log.e("SendBitcoinUC", " Create failed: ${createResult.message}")
                return@withContext createResult
            }

            val transaction = (createResult as Result.Success).data
            Log.d("SendBitcoinUC", " Transaction created: ${transaction.id}")

            // 2. Sign transaction
            Log.d("SendBitcoinUC", "Step 2: Signing transaction...")
            val signResult = signBitcoinTransactionUseCase(transaction.id)

            if (signResult is Result.Error) {
                Log.e("SendBitcoinUC", " Sign failed: ${signResult.message}")
                return@withContext signResult
            }

            val signedTransaction = (signResult as Result.Success).data
            Log.d("SendBitcoinUC", " Transaction signed: ${signedTransaction.txHash}")

            // 3. Broadcast transaction
            Log.d("SendBitcoinUC", "Step 3: Broadcasting transaction...")
            val broadcastResult = broadcastBitcoinTransactionUseCase(transaction.id)

            when (broadcastResult) {
                is Result.Success -> {
                    val result = broadcastResult.data
                    Log.d("SendBitcoinUC", " Broadcast result: success=${result.success}, hash=${result.hash}")

                    val sendResult = SendBitcoinResult(
                        transactionId = transaction.id,
                        txHash = result.hash ?: signedTransaction.txHash ?: "",
                        success = result.success,
                        error = result.error
                    )

                    Log.d("SendBitcoinUC", "========== SEND COMPLETE ==========")
                    Result.Success(sendResult)
                }
                is Result.Error -> {
                    Log.e("SendBitcoinUC", " Broadcast failed: ${broadcastResult.message}")
                    broadcastResult
                }
                Result.Loading -> {
                    Log.d("SendBitcoinUC", " Broadcast loading...")
                    Result.Error("Broadcast timeout", null)
                }
            }

        } catch (e: Exception) {
            Log.e("SendBitcoinUC", " Exception: ${e.message}", e)
            Result.Error("Send failed: ${e.message}", e)
        }
    }
}

data class SendBitcoinResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)

@Singleton
class CreateBitcoinTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<BitcoinTransaction> {
        return try {
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

            val bitcoinCoin = wallet.bitcoin
                ?: return Result.Error("Bitcoin not enabled for this wallet", IllegalArgumentException("Bitcoin not enabled"))

            createBitcoinTransaction(wallet.id, bitcoinCoin, toAddress, amount, feeLevel, note)
        } catch (e: Exception) {
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }

    private suspend fun createBitcoinTransaction(
        walletId: String,
        bitcoinCoin: BitcoinCoin,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<BitcoinTransaction> = withContext(Dispatchers.IO) {
        try {
            Log.d("CreateBitcoinTxUC", " createBitcoinTransaction START")
            Log.d("CreateBitcoinTxUC", "Wallet: ${bitcoinCoin.address}")
            Log.d("CreateBitcoinTxUC", "Amount: $amount BTC to $toAddress")

            val feeResult = bitcoinBlockchainRepository.getFeeEstimate(feeLevel)

            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                is Result.Error -> return@withContext Result.Error(
                    "Failed to get fee estimate: ${feeResult.message}",
                    feeResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Timeout getting fee estimate", null)
            }

            val feePerByteString = feeEstimate.feePerByte ?: "1.0"
            val feePerByte = feePerByteString.toDoubleOrNull() ?: 1.0

            val satoshis = amount.multiply(BigDecimal("100000000")).toLong()
            val estimatedSize = 250L
            val fee = (estimatedSize * feePerByte).toLong()

            val totalFeeDecimal = feeEstimate.totalFeeDecimal.ifBlank {
                BigDecimal(fee).divide(BigDecimal("100000000"), 8, RoundingMode.HALF_UP)
                    .toPlainString()
            }

            val transaction = BitcoinTransaction(
                id = "btc_tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = bitcoinCoin.address,
                toAddress = toAddress,
                amountSatoshis = satoshis,
                amountBtc = amount.toPlainString(),
                feeSatoshis = fee,
                feeBtc = BigDecimal(totalFeeDecimal).toPlainString(),
                feePerByte = feePerByte,
                estimatedSize = estimatedSize,
                signedHex = null,
                txHash = null,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = bitcoinCoin.network.name
            )

            bitcoinTransactionRepository.saveTransaction(transaction)
            Log.d("CreateBitcoinTxUC", " Saved LOCAL transaction")
            Result.Success(transaction)

        } catch (e: Exception) {
            Log.e("CreateBitcoinTxUC", " Error: ${e.message}", e)
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }
}

@Singleton
class SignBitcoinTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val keyManager: KeyManager,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository
) {
    suspend operator fun invoke(transactionId: String): Result<BitcoinTransaction> = withContext(Dispatchers.IO) {
        try {
            val transaction = bitcoinTransactionRepository.getTransaction(transactionId)
                ?: return@withContext Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: return@withContext Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

            val bitcoinCoin = wallet.bitcoin
                ?: return@withContext Result.Error("Bitcoin not enabled for this wallet", IllegalArgumentException("Bitcoin not enabled"))

            Log.d("SignBitcoinTxUC", " Signing transaction: ${transaction.id}")

            val privateKeyResult = keyManager.getPrivateKeyForSigning(
                transaction.walletId,
                keyType = "BTC_PRIVATE_KEY"
            )

            if (privateKeyResult.isFailure) {
                return@withContext Result.Error(
                    privateKeyResult.exceptionOrNull()?.message ?: "No private key",
                    privateKeyResult.exceptionOrNull()
                )
            }

            val privateKeyWIF = privateKeyResult.getOrThrow()

            val networkParams = when (bitcoinCoin.network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
                else -> MainNetParams.get()
            }

            val ecKey = try {
                DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key
            } catch (e: Exception) {
                return@withContext Result.Error("Invalid private key format", e)
            }

            val derivedAddress = LegacyAddress.fromKey(networkParams, ecKey).toString()
            if (derivedAddress != bitcoinCoin.address) {
                return@withContext Result.Error("Private key doesn't match wallet", IllegalStateException("Address mismatch"))
            }

            val satoshis = transaction.amountSatoshis
            val toAddress = transaction.toAddress

            val signResult = bitcoinBlockchainRepository.createAndSignTransaction(
                fromKey = ecKey,
                toAddress = toAddress,
                satoshis = satoshis,
                network = bitcoinCoin.network
            )

            val signedTx = when (signResult) {
                is Result.Success -> signResult.data
                is Result.Error -> return@withContext Result.Error(
                    "Failed to sign: ${signResult.message}",
                    signResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Timeout signing", null)
            }

            val txHash = signedTx.txId.toString()
            val signedHex = Utils.HEX.encode(signedTx.bitcoinSerialize())

            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                txHash = txHash,
                signedHex = signedHex
            )

            bitcoinTransactionRepository.updateTransaction(updatedTransaction)

            Result.Success(updatedTransaction)

        } catch (e: Exception) {
            Log.e("SignBitcoinTxUC", " Signing failed: ${e.message}", e)
            Result.Error("Signing failed: ${e.message}", e)
        }
    }
}

@Singleton
class BroadcastBitcoinTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository
) {
    suspend operator fun invoke(transactionId: String): Result<BroadcastResult> = withContext(Dispatchers.IO) {
        try {
            val transaction = bitcoinTransactionRepository.getTransaction(transactionId)
                ?: return@withContext Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            val signedHex = transaction.signedHex
                ?: return@withContext Result.Error("Transaction not signed", IllegalStateException("Not signed"))

            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: return@withContext Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

            val bitcoinCoin = wallet.bitcoin
                ?: return@withContext Result.Error("Bitcoin not enabled for this wallet", IllegalArgumentException("Bitcoin not enabled"))

            val broadcastResult = bitcoinBlockchainRepository.broadcastTransaction(
                signedHex = signedHex,
                network = bitcoinCoin.network
            )

            when (broadcastResult) {
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
                    Result.Success(result)
                }
                is Result.Error -> Result.Error(broadcastResult.message, broadcastResult.throwable)
                Result.Loading -> Result.Error("Broadcast timeout", null)
            }

        } catch (e: Exception) {
            Log.e("BroadcastBitcoinUC", " Broadcast error: ${e.message}", e)
            Result.Error("Broadcast failed: ${e.message}", e)
        }
    }
}

@Singleton
class GetBitcoinFeeEstimateUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository
) {
    suspend operator fun invoke(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<FeeEstimate> {
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