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

@Singleton
class BitcoinTransactionRepository @Inject constructor(
    private val localDataSource: TransactionLocalDataSource,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val walletRepository: WalletRepository,
    private val keyManager: KeyManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun createSendTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendTransaction> {
        return try {
            val wallet = walletRepository.getWallet(walletId) as? BitcoinWallet
                ?: return Result.failure(IllegalArgumentException("Bitcoin wallet not found"))

            createBitcoinTransaction(
                wallet = wallet,
                toAddress = toAddress,
                amount = amount,
                feeLevel = feeLevel,
                note = note
            )
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
        Log.d("BitcoinTxRepo", " createBitcoinTransaction START")
        Log.d("BitcoinTxRepo", "Wallet: ${wallet.address}")
        Log.d("BitcoinTxRepo", "Amount: $amount BTC to $toAddress")

        return withContext(Dispatchers.IO) {
            try {
                // 1. Get fee estimate based on fee level
                Log.d("BitcoinTxRepo", "Getting fee estimate...")
                val feeEstimate = bitcoinBlockchainRepository.getFeeEstimate(feeLevel)

                val feePerByteString = feeEstimate.feePerByte ?: "1.0"
                val feePerByte = feePerByteString.toDoubleOrNull() ?: 1.0
                Log.d("BitcoinTxRepo", "Fee per byte: $feePerByte sat/vB")
                Log.d("BitcoinTxRepo", "Total fee: ${feeEstimate.totalFee}")

                // 2. Convert amount to satoshis (1 BTC = 100,000,000 satoshis)
                val satoshis = amount.multiply(BigDecimal("100000000")).toLong()
                Log.d("BitcoinTxRepo", "Amount in satoshis: $satoshis")

                // 3. Estimate transaction size (roughly 140-250 bytes for typical transaction)
                // For Bitcoin, fee is per byte, not per transaction
                val estimatedSize = 250L

                // Calculate fee from feePerByte (not from totalFee which might be decimal)
                val fee = (estimatedSize * feePerByte).toLong()
                Log.d("BitcoinTxRepo", "Estimated fee: $fee satoshis")

                // 4. Handle totalFeeDecimal if it exists (for display)
                val totalFeeDecimal = if (feeEstimate.totalFeeDecimal.isNotBlank()) {
                    feeEstimate.totalFeeDecimal
                } else {
                    BigDecimal(fee).divide(BigDecimal("100000000"), 8, RoundingMode.HALF_UP).toPlainString()
                }

                // 5. Create LOCAL transaction record
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
                    gasPrice = null,
                    gasLimit = null,
                    signedHex = null,
                    nonce = 0, // Bitcoin doesn't use nonces
                    hash = null,
                    timestamp = System.currentTimeMillis(),
                    feeLevel = feeLevel,
                    metadata = mapOf(
                        "estimatedSize" to estimatedSize.toString(),
                        "network" to wallet.network.name,
                        "feePerByte" to feePerByte.toString()
                    )
                )

                // 6. Save to local storage only
                localDataSource.saveSendTransaction(transaction)
                Log.d("BitcoinTxRepo", " Saved LOCAL transaction (not signed/broadcasted)")

                Result.success(transaction)

            } catch (e: Exception) {
                Log.e("BitcoinTxRepo", " Error in createBitcoinTransaction: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun signTransaction(transactionId: String): Result<SignedTransaction> {
        return withContext(Dispatchers.IO) {
            try {
                val transaction = localDataSource.getSendTransaction(transactionId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Transaction not found"))

                // Only support Bitcoin
                if (transaction.walletType != WalletType.BITCOIN) {
                    return@withContext Result.failure(IllegalArgumentException("Only Bitcoin signing supported"))
                }

                signBitcoinTransactionReal(transactionId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Real Bitcoin signing implementation using bitcoinj
     */
    private suspend fun signBitcoinTransactionReal(
        transactionId: String
    ): Result<SignedTransaction> {
        return withContext(Dispatchers.IO) {
            try {
                val transaction = localDataSource.getSendTransaction(transactionId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Transaction not found"))

                // Get wallet
                val wallet = walletRepository.getWallet(transaction.walletId) as? BitcoinWallet
                    ?: return@withContext Result.failure(IllegalArgumentException("Bitcoin wallet not found"))

                Log.d("BitcoinTxRepo", " Signing transaction: ${transaction.id}")

                // 1. Get private key
                Log.d("BitcoinTxRepo", "Requesting private key...")
                val privateKeyResult = keyManager.getPrivateKeyForSigning(
                    transaction.walletId,
                    keyType = "BTC_PRIVATE_KEY"
                )

                if (privateKeyResult.isFailure) {
                    Log.e("BitcoinTxRepo", "Failed to get private key")
                    return@withContext Result.failure(
                        privateKeyResult.exceptionOrNull() ?: IllegalStateException("No private key")
                    )
                }

                val privateKeyWIF = privateKeyResult.getOrThrow()
                Log.d("BitcoinTxRepo", "✓ Got private key: ${privateKeyWIF.take(8)}...")

                // 2. Get network parameters
                val networkParams = when (wallet.network) {
                    BitcoinNetwork.MAINNET -> MainNetParams.get()
                    BitcoinNetwork.TESTNET -> TestNet3Params.get()
                    BitcoinNetwork.REGTEST -> RegTestParams.get()
                }

                // 3. Create bitcoinj Key from private key
                val ecKey = DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key

                // Verify address matches
                val derivedAddress = LegacyAddress.fromKey(networkParams, ecKey).toString()
                if (derivedAddress != wallet.address) {
                    Log.e(
                        "BitcoinTxRepo",
                        "Address mismatch! Expected: ${wallet.address}, Got: $derivedAddress"
                    )
                    return@withContext Result.failure(IllegalStateException("Private key doesn't match wallet"))
                }

                // 4. Create and sign transaction with bitcoinj
                val satoshis = transaction.amount.toLongOrNull() ?: 0L
                val toAddress = transaction.toAddress

                val signedTx = bitcoinBlockchainRepository.createAndSignTransaction(
                    fromKey = ecKey,
                    toAddress = toAddress,
                    satoshis = satoshis,
                    network = wallet.network
                )

                // 5. Get transaction hash
                val txHash = signedTx.txId.toString()
                val signedHex = Utils.HEX.encode(signedTx.bitcoinSerialize())

                Log.d("BitcoinTxRepo", "Signed! Hash: $txHash")

                // 6. Create signed transaction
                val signedTransaction = SignedTransaction(
                    rawHex = signedHex,
                    hash = txHash,
                    chain = transaction.chain
                )

                // 7. Update transaction with signed data
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
                localDataSource.saveSendTransaction(updatedTransaction)

                Result.success(signedTransaction)

            } catch (e: Exception) {
                Log.e("BitcoinTxRepo", " Signing failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun broadcastTransaction(transactionId: String): Result<BroadcastResult> {
        return withContext(Dispatchers.IO) {
            try {
                val transaction = localDataSource.getSendTransaction(transactionId)
                    ?: run {
                        Log.e("BitcoinTxRepo", " Transaction not found")
                        return@withContext Result.failure(IllegalArgumentException("Transaction not found"))
                    }

                Log.d(
                    "BitcoinTxRepo",
                    "Transaction found: ${transaction.id}, chain: ${transaction.chain}"
                )

                // Only handle Bitcoin
                when (transaction.chain) {
                    ChainType.BITCOIN -> {
                        Log.d("BitcoinTxRepo", " Using real broadcast for Bitcoin")
                        broadcastTransactionReal(transactionId)
                    }
                    else -> {
                        Log.w("BitcoinTxRepo", "⚠ Chain ${transaction.chain} not supported")
                        Result.success(
                            BroadcastResult(
                                success = false,
                                error = "Chain ${transaction.chain} not implemented",
                                chain = transaction.chain
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("BitcoinTxRepo", " Broadcast failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun broadcastTransactionReal(transactionId: String): Result<BroadcastResult> {
        return withContext(Dispatchers.IO) {
            try {
                val transaction = localDataSource.getSendTransaction(transactionId)
                    ?: run {
                        Log.e("BitcoinBroadcast", " Transaction not found")
                        return@withContext Result.failure(IllegalArgumentException("Transaction not found"))
                    }

                Log.d("BitcoinBroadcast", "Found transaction: ${transaction.id}")
                Log.d("BitcoinBroadcast", "Signed hex available: ${transaction.signedHex != null}")

                // 1. Check if transaction is signed
                val signedHex = transaction.signedHex
                    ?: run {
                        Log.e("BitcoinBroadcast", " Transaction not signed")
                        return@withContext Result.failure(IllegalStateException("Transaction not signed"))
                    }

                // 2. Get wallet for network info
                val wallet = walletRepository.getWallet(transaction.walletId) as? BitcoinWallet
                    ?: return@withContext Result.failure(IllegalArgumentException("Bitcoin wallet not found"))

                // 3. Broadcast to Bitcoin network
                Log.d("BitcoinBroadcast", " Broadcasting transaction to Bitcoin network...")
                val broadcastResult = bitcoinBlockchainRepository.broadcastTransaction(
                    signedHex = signedHex,
                    network = wallet.network
                )

                Log.d("BitcoinBroadcast", "Broadcast result: success=${broadcastResult.success}")
                Log.d("BitcoinBroadcast", "Broadcast hash: ${broadcastResult.hash}")

                // 4. Update transaction status
                val updatedTransaction = if (broadcastResult.success) {
                    Log.d("BitcoinBroadcast", " Transaction broadcast successful!")
                    transaction.copy(
                        status = TransactionStatus.SUCCESS,
                        hash = broadcastResult.hash ?: transaction.hash
                    )
                } else {
                    Log.e("BitcoinBroadcast", " Transaction broadcast failed: ${broadcastResult.error}")
                    transaction.copy(
                        status = TransactionStatus.FAILED
                    )
                }
                localDataSource.saveSendTransaction(updatedTransaction)

                Result.success(broadcastResult)

            } catch (e: Exception) {
                Log.e("BitcoinBroadcast", " Broadcast error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun validateBitcoinAddress(address: String, network: BitcoinNetwork): Boolean {
        return bitcoinBlockchainRepository.validateAddress(address, network)
    }

    suspend fun getFeeEstimate(feeLevel: FeeLevel = FeeLevel.NORMAL): FeeEstimate {
        return bitcoinBlockchainRepository.getFeeEstimate(feeLevel)
    }

    suspend fun getSendTransaction(transactionId: String): SendTransaction? {
        return localDataSource.getSendTransaction(transactionId)
    }
}
