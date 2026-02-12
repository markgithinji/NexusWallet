package com.example.nexuswallet.feature.coin.bitcoin

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
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


@Singleton
class CreateBitcoinTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendTransaction> {
        return try {
            val wallet = walletRepository.getWallet(walletId) as? BitcoinWallet
                ?: return Result.Error("Bitcoin wallet not found", IllegalArgumentException("Wallet not found"))

            createBitcoinTransaction(wallet, toAddress, amount, feeLevel, note)
        } catch (e: Exception) {
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }

    private suspend fun createBitcoinTransaction(
        wallet: BitcoinWallet,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SendTransaction> = withContext(Dispatchers.IO) {
        try {
            Log.d("CreateBitcoinTxUC", " createBitcoinTransaction START")
            Log.d("CreateBitcoinTxUC", "Wallet: ${wallet.address}")
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

            val transaction = SendTransaction(
                id = "btc_tx_${System.currentTimeMillis()}",
                walletId = wallet.id,
                walletType = WalletType.BITCOIN,
                fromAddress = wallet.address,
                toAddress = toAddress,
                amount = satoshis.toString(),
                amountDecimal = amount.toPlainString(),
                fee = fee.toString(),
                feeDecimal = totalFeeDecimal,
                total = (satoshis + fee).toString(),
                totalDecimal = (amount + BigDecimal(totalFeeDecimal)).toPlainString(),
                chain = ChainType.BITCOIN,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                metadata = mapOf(
                    "estimatedSize" to estimatedSize.toString(),
                    "network" to wallet.network.name,
                    "feePerByte" to feePerByte.toString()
                )
            )

            transactionLocalDataSource.saveSendTransaction(transaction)
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
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(transactionId: String): Result<SignedTransaction> = withContext(Dispatchers.IO) {
        try {
            val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                ?: return@withContext Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            if (transaction.walletType != WalletType.BITCOIN) {
                return@withContext Result.Error("Only Bitcoin signing supported", IllegalArgumentException("Wrong wallet type"))
            }

            val wallet = walletRepository.getWallet(transaction.walletId) as? BitcoinWallet
                ?: return@withContext Result.Error("Bitcoin wallet not found", IllegalArgumentException("Wallet not found"))

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

            val networkParams = when (wallet.network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
                BitcoinNetwork.REGTEST -> RegTestParams.get()
            }

            val ecKey = try {
                DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key
            } catch (e: Exception) {
                return@withContext Result.Error("Invalid private key format", e)
            }

            val derivedAddress = LegacyAddress.fromKey(networkParams, ecKey).toString()
            if (derivedAddress != wallet.address) {
                return@withContext Result.Error("Private key doesn't match wallet", IllegalStateException("Address mismatch"))
            }

            val satoshis = transaction.amount.toLongOrNull() ?: 0L
            val toAddress = transaction.toAddress

            val signResult = bitcoinBlockchainRepository.createAndSignTransaction(
                fromKey = ecKey,
                toAddress = toAddress,
                satoshis = satoshis,
                network = wallet.network
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

            val signedTransaction = SignedTransaction(
                rawHex = signedHex,
                hash = txHash,
                chain = transaction.chain
            )

            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                hash = txHash,
                signedHex = signedHex,
                metadata = transaction.metadata + mapOf(
                    "inputCount" to signedTx.inputs.size.toString(),
                    "outputCount" to signedTx.outputs.size.toString(),
                    "size" to signedTx.bitcoinSerialize().size.toString()
                )
            )
            transactionLocalDataSource.saveSendTransaction(updatedTransaction)

            Result.Success(signedTransaction)

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
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(transactionId: String): Result<BroadcastResult> = withContext(Dispatchers.IO) {
        try {
            val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                ?: return@withContext Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            if (transaction.chain != ChainType.BITCOIN) {
                return@withContext Result.Success(
                    BroadcastResult(
                        success = false,
                        error = "Chain ${transaction.chain} not supported",
                        chain = transaction.chain
                    )
                )
            }

            val signedHex = transaction.signedHex
                ?: return@withContext Result.Error("Transaction not signed", IllegalStateException("Not signed"))

            val wallet = walletRepository.getWallet(transaction.walletId) as? BitcoinWallet
                ?: return@withContext Result.Error("Bitcoin wallet not found", IllegalArgumentException("Wallet not found"))

            val broadcastResult = bitcoinBlockchainRepository.broadcastTransaction(
                signedHex = signedHex,
                network = wallet.network
            )

            when (broadcastResult) {
                is Result.Success -> {
                    val result = broadcastResult.data
                    val updatedTransaction = if (result.success) {
                        transaction.copy(
                            status = TransactionStatus.SUCCESS,
                            hash = result.hash ?: transaction.hash
                        )
                    } else {
                        transaction.copy(
                            status = TransactionStatus.FAILED
                        )
                    }
                    transactionLocalDataSource.saveSendTransaction(updatedTransaction)
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
                BitcoinNetwork.REGTEST -> RegTestParams.get()
            }
            try {
                LegacyAddress.fromBase58(params, address)
                true
            } catch (e: AddressFormatException) {
                try {
                    Bech32.decode(address)
                    true
                } catch (e: AddressFormatException) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}