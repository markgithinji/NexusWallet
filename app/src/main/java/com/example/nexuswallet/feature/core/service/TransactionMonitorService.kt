package com.example.nexuswallet.feature.core.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result as WorkResult
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.core.util.Result as AppResult
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TransactionMonitorService @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bitcoinRepository: BitcoinBlockchainRepository,
    private val evmRepository: EVMBlockchainRepository,
    private val solanaRepository: SolanaBlockchainRepository,
    private val notificationService: NotificationService,
    private val logger: Logger
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TxMonitorService"
        
        const val KEY_TX_HASH = "tx_hash"
        const val KEY_NETWORK_NAME = "network_name"
        const val KEY_NETWORK_TYPE = "network_type"
        const val KEY_COIN_SYMBOL = "coin_symbol"
        const val KEY_AMOUNT = "amount"
        
        const val NETWORK_BITCOIN = "bitcoin"
        const val NETWORK_ETHEREUM = "ethereum"
        const val NETWORK_SOLANA = "solana"

        fun enqueue(
            context: Context,
            txHash: String,
            networkType: String,
            networkName: String,
            coinSymbol: String,
            amount: String
        ) {
            val data = Data.Builder()
                .putString(KEY_TX_HASH, txHash)
                .putString(KEY_NETWORK_TYPE, networkType)
                .putString(KEY_NETWORK_NAME, networkName)
                .putString(KEY_COIN_SYMBOL, coinSymbol)
                .putString(KEY_AMOUNT, amount)
                .build()

            val request = OneTimeWorkRequestBuilder<TransactionMonitorService>()
                .setInputData(data)
                .setInitialDelay(15, TimeUnit.SECONDS) // Wait a bit for first check
                .addTag("monitor_$txHash")
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): WorkResult {
        val txHash = inputData.getString(KEY_TX_HASH) ?: return WorkResult.failure()
        val networkType = inputData.getString(KEY_NETWORK_TYPE) ?: return WorkResult.failure()
        val networkName = inputData.getString(KEY_NETWORK_NAME) ?: ""
        val coinSymbol = inputData.getString(KEY_COIN_SYMBOL) ?: ""
        val amount = inputData.getString(KEY_AMOUNT) ?: ""

        logger.d(TAG, "Monitoring transaction: $txHash on $networkName")

        if (runAttemptCount > 30) { // Max ~30 mins
            logger.w(TAG, "Giving up on monitoring tx $txHash after $runAttemptCount attempts")
            return WorkResult.failure()
        }

        val statusResult = when (networkType) {
            NETWORK_BITCOIN -> {
                val network = if (networkName.contains("Testnet")) BitcoinNetwork.Testnet else BitcoinNetwork.Mainnet
                bitcoinRepository.getTransactionStatus(txHash, network)
            }
            NETWORK_ETHEREUM -> {
                val network = if (networkName.contains("Sepolia")) EthereumNetwork.Sepolia else EthereumNetwork.Mainnet
                evmRepository.getTransactionStatus(txHash, network)
            }
            NETWORK_SOLANA -> {
                val network = if (networkName.contains("Devnet")) SolanaNetwork.Devnet else SolanaNetwork.Mainnet
                solanaRepository.getTransactionStatus(txHash, network)
            }
            else -> null
        }

        return when (statusResult) {
            is AppResult.Success -> {
                logger.d(TAG, "Transaction $txHash status: ${statusResult.data}")
                when (statusResult.data) {
                    TransactionStatus.SUCCESS -> {
                        notificationService.showTransactionNotification(
                            title = "Transaction Confirmed!",
                            message = "Your $amount $coinSymbol has been successfully sent on $networkName.",
                            txHash = txHash
                        )
                        WorkResult.success()
                    }
                    TransactionStatus.FAILED -> {
                        notificationService.showTransactionNotification(
                            title = "Transaction Failed",
                            message = "Your $amount $coinSymbol transaction on $networkName has failed.",
                            txHash = txHash
                        )
                        WorkResult.success()
                    }
                    TransactionStatus.PENDING -> {
                        WorkResult.retry()
                    }
                }
            }
            is AppResult.Error -> {
                logger.e(TAG, "Error checking status for $txHash: ${statusResult.message}")
                WorkResult.retry()
            }
            else -> {
                WorkResult.retry()
            }
        }
    }
}
