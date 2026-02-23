package com.example.nexuswallet.feature.coin.bitcoin

import android.util.Log
import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
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
        Log.d("SyncBitcoinUC", "Syncing Bitcoin transactions for wallet: $walletId")

        // Business logic validation
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

        return@withContext when (val transactionsResult = bitcoinBlockchainRepository.getAddressTransactions(
            address = bitcoinCoin.address,
            network = bitcoinCoin.network
        )) {
            is Result.Success -> {
                val transactions = transactionsResult.data

                // Clear and save
                bitcoinTransactionRepository.deleteAllForWallet(walletId)
                transactions.forEach { tx ->
                    val domainTx = tx.toDomain(
                        walletId = walletId,
                        isIncoming = tx.isIncoming,
                        network = bitcoinCoin.network
                    )
                    bitcoinTransactionRepository.saveTransaction(domainTx)
                }

                Log.d("SyncBitcoinUC", "Sync completed for wallet $walletId")
                Result.Success(Unit)
            }
            is Result.Error -> {
                Log.e("SyncBitcoinUC", "Failed to sync: ${transactionsResult.message}")
                Result.Error(transactionsResult.message)
            }
            else -> Result.Error("Unknown error")
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
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendBitcoinResult> = withContext(Dispatchers.IO) {
        Log.d("SendBitcoinUC", "Sending ${amount.toPlainString()} BTC to ${toAddress.take(8)}...")

        val wallet = walletRepository.getWallet(walletId) ?:
        return@withContext Result.Error("Wallet not found")

        val bitcoinCoin = wallet.bitcoin ?:
        return@withContext Result.Error("Bitcoin not enabled")

        if (!validateAddress(toAddress, bitcoinCoin.network))
            return@withContext Result.Error("Invalid address for ${bitcoinCoin.network}")

        // Get fee estimate
        val feeResult = bitcoinBlockchainRepository.getFeeEstimate(feeLevel, 1, 2)
        if (feeResult is Result.Error)
            return@withContext Result.Error("Failed to get fee estimate: ${feeResult.message}")

        val feeEstimate = (feeResult as Result.Success).data
        val satoshis = amount.multiply(BigDecimal("100000000")).toLong()

        // Create transaction
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

        // Sign transaction
        val signedTransaction = signTransaction(transaction, bitcoinCoin) ?:
        return@withContext Result.Error("Failed to sign transaction")

        // Broadcast
        return@withContext when (val broadcastResult = bitcoinBlockchainRepository.broadcastTransaction(
            signedHex = signedTransaction.signedHex!!,
            network = bitcoinCoin.network
        )) {
            is Result.Success -> {
                val updatedTx = transaction.copy(
                    status = TransactionStatus.SUCCESS,
                    txHash = broadcastResult.data
                )
                bitcoinTransactionRepository.updateTransaction(updatedTx)

                Result.Success(SendBitcoinResult(
                    transactionId = transaction.id,
                    txHash = broadcastResult.data,
                    success = true,
                    error = null
                ))
            }
            is Result.Error -> {
                val failedTx = transaction.copy(status = TransactionStatus.FAILED)
                bitcoinTransactionRepository.updateTransaction(failedTx)

                Result.Error(broadcastResult.message)
            }
            else -> Result.Error("Unknown error")
        }
    }

    private suspend fun signTransaction(
        transaction: BitcoinTransaction,
        bitcoinCoin: BitcoinCoin
    ): BitcoinTransaction? {
        // Get private key
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = transaction.walletId,
            keyType = "BTC_PRIVATE_KEY"
        ) ?: return null

        val privateKeyWIF = keyStoreRepository.decryptString(
            encryptedData.first,
            encryptedData.second.toHex()
        )

        val networkParams = when (bitcoinCoin.network) {
            BitcoinNetwork.MAINNET -> MainNetParams.get()
            BitcoinNetwork.TESTNET -> TestNet3Params.get()
        }

        val ecKey = DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key

        // Verify key matches address
        if (LegacyAddress.fromKey(networkParams, ecKey).toString() != bitcoinCoin.address)
            return null

        // Sign
        return when (val signResult = bitcoinBlockchainRepository.createAndSignTransaction(
            fromKey = ecKey,
            toAddress = transaction.toAddress,
            satoshis = transaction.amountSatoshis,
            feeLevel = transaction.feeLevel,
            network = bitcoinCoin.network
        )) {
            is Result.Success -> {
                val signedTx = signResult.data
                transaction.copy(
                    status = TransactionStatus.PENDING,
                    txHash = signedTx.txId.toString(),
                    signedHex = Utils.HEX.encode(signedTx.bitcoinSerialize())
                ).also { bitcoinTransactionRepository.updateTransaction(it) }
            }
            else -> null
        }
    }

    private fun validateAddress(address: String, network: BitcoinNetwork): Boolean = try {
        Address.fromString(when (network) {
            BitcoinNetwork.MAINNET -> MainNetParams.get()
            BitcoinNetwork.TESTNET -> TestNet3Params.get()
        }, address)
        true
    } catch (e: Exception) {
        false
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
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
        return bitcoinBlockchainRepository.getFeeEstimate(feeLevel, inputCount, outputCount)
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