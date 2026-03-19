package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.EVMTransaction
import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.core.domain.model.Transaction
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllTransactionsUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher
) {

    private val tag = "GetAllTransactionsUC"

    /**
     * Returns a reactive stream of transactions.
     * 1. Emits cached data immediately from DB.
     * 2. Triggers a background sync as a side effect.
     * 3. Automatically emits updated data once the sync writes to the DB.
     */
    operator fun invoke(walletId: String, forceRefresh: Boolean = false): Flow<List<Transaction>> {
        return combine(
            bitcoinTransactionRepository.getTransactions(walletId, BitcoinNetwork.Mainnet),
            bitcoinTransactionRepository.getTransactions(walletId, BitcoinNetwork.Testnet),
            evmTransactionRepository.getTransactions(walletId),
            solanaTransactionRepository.getTransactions(walletId, SolanaNetwork.Mainnet),
            solanaTransactionRepository.getTransactions(walletId, SolanaNetwork.Devnet)
        ) { flows ->
            flows.flatMap { it }
                .sortedByDescending { it.timestamp }
                .also { logSummary(it) }
        }
            .onStart {
                // Only trigger the background sync if forceRefresh is requested
                if (forceRefresh) {
                    logger.d(tag, "Force refresh requested for wallet: $walletId")
                    launchSyncTransactions(walletId)
                }
            }
            .distinctUntilChanged()
    }

    private fun launchSyncTransactions(walletId: String) {
        CoroutineScope(ioDispatcher).launch {
            try {
                syncTransactions(walletId)
            } catch (e: Exception) {
                logger.e(tag, "Background sync failed", e)
            }
        }
    }

    private suspend fun syncTransactions(walletId: String) = coroutineScope {
        val wallet = walletRepository.getWallet(walletId) ?: return@coroutineScope

        val jobs = mutableListOf<Job>()

        wallet.bitcoinCoins.forEach { jobs.add(launch { syncBitcoin(walletId, it) }) }
        wallet.evmTokens.forEach { jobs.add(launch { syncEVM(walletId, it) }) }
        wallet.solanaCoins.forEach { jobs.add(launch { syncSolana(walletId, it) }) }

        jobs.joinAll()
    }

    // ============ BITCOIN SYNC ============

    private suspend fun syncBitcoin(walletId: String, coin: BitcoinCoin) {
        try {
            val result = bitcoinBlockchainRepository.getAddressTransactions(
                walletId = walletId,
                address = coin.address,
                network = coin.network
            )
            if (result is Result.Success && result.data.isNotEmpty()) {
                bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, coin.network)
                result.data.forEach { bitcoinTransactionRepository.saveTransaction(it) }
                logger.d(tag, "Saved ${result.data.size} Bitcoin transactions for ${coin.network}")
            }
        } catch (e: Exception) {
            logger.e(tag, "Error syncing Bitcoin for ${coin.network}", e)
        }
    }

    // ============ EVM SYNC ============

    private suspend fun syncEVM(walletId: String, token: EVMToken) {
        try {
            if (token is NativeETH) {
                // Sync Native (ETH) transactions
                val nativeRes = evmBlockchainRepository.getNativeTransactions(
                    address = token.address,
                    network = token.network,
                    walletId = walletId,
                    evmTokenType = token.evmTokenType
                )
                if (nativeRes is Result.Success && nativeRes.data.isNotEmpty()) {
                    nativeRes.data.forEach { evmTransactionRepository.saveTransaction(it) }
                    logger.d(
                        tag,
                        "Saved ${nativeRes.data.size} Native ETH transactions for ${token.network}"
                    )
                }
            } else {
                // Sync Token Transactions (USDC, USDT)
                val tokenRes = evmBlockchainRepository.getTokenTransactions(
                    address = token.address,
                    tokenContract = token.contractAddress,
                    network = token.network,
                    walletId = walletId,
                    evmTokenType = token.evmTokenType
                )
                if (tokenRes is Result.Success && tokenRes.data.isNotEmpty()) {
                    tokenRes.data.forEach { evmTransactionRepository.saveTransaction(it) }
                    logger.d(
                        tag,
                        "Saved ${tokenRes.data.size} ${token.evmTokenType} transactions for ${token.network}"
                    )
                }
            }
        } catch (e: Exception) {
            logger.e(tag, "Error syncing EVM for ${token.network}", e)
        }
    }

    // ============ SOLANA SYNC ============

    private suspend fun syncSolana(walletId: String, coin: SolanaCoin) {
        try {
            val result = solanaBlockchainRepository.getTransactions(
                walletId = walletId,
                address = coin.address,
                network = coin.network,
                limit = 50
            )
            if (result is Result.Success && result.data.isNotEmpty()) {
                solanaTransactionRepository.deleteForWalletAndNetwork(walletId, coin.network)
                result.data.forEach { solanaTransactionRepository.saveTransaction(it) }
                logger.d(tag, "Saved ${result.data.size} Solana transactions for ${coin.network}")
            }
        } catch (e: Exception) {
            logger.e(tag, "Error syncing Solana for ${coin.network}", e)
        }
    }

    private fun logSummary(list: List<Transaction>) {
        val btcCount = list.count { it is BitcoinTransaction }
        val evmCount = list.count { it is EVMTransaction }
        val solCount = list.count { it is SolanaTransaction }
        logger.d(
            tag,
            "Emitting - BTC: $btcCount, EVM: $evmCount, SOL: $solCount, Total: ${list.size}"
        )
    }
}