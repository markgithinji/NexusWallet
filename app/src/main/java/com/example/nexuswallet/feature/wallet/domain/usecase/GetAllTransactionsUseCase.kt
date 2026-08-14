package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
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
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.Network
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Returns a reactive stream of transactions.
     * 1. Emits cached data immediately from DB.
     * 2. Triggers a background sync as a side effect.
     * 3. Automatically emits updated data once the sync writes to the DB.
     */
    operator fun invoke(
        walletId: String,
        forceRefresh: Boolean = false,
        networkFilter: Network? = null
    ): Flow<List<Transaction>> {
        return combine(
            bitcoinTransactionRepository.getTransactions(walletId, BitcoinNetwork.Mainnet),
            bitcoinTransactionRepository.getTransactions(walletId, BitcoinNetwork.Testnet),
            evmTransactionRepository.getTransactions(walletId),
            solanaTransactionRepository.getTransactions(walletId, SolanaNetwork.Mainnet),
            solanaTransactionRepository.getTransactions(walletId, SolanaNetwork.Devnet)
        ) { flows ->
            flows.flatMap { it }
                .sortedByDescending { it.timestamp }
        }
            .onStart {
                // Only trigger the background sync if forceRefresh is requested
                if (forceRefresh) {
                    launchSyncTransactions(walletId, networkFilter)
                }
            }
            .distinctUntilChanged()
    }

    private fun launchSyncTransactions(walletId: String, networkFilter: Network? = null) {
        CoroutineScope(ioDispatcher).launch {
            try {
                syncTransactions(walletId, networkFilter)
            } catch (e: Exception) {
                logger.e(TAG, "Background sync failed", e)
            }
        }
    }

    private suspend fun syncTransactions(walletId: String, networkFilter: Network? = null) =
        coroutineScope {
            val wallet = walletRepository.getWallet(walletId) ?: return@coroutineScope

            val jobs = mutableListOf<Job>()

            // Bitcoin: Only if no filter or Bitcoin signal
            if (networkFilter == null || networkFilter is BitcoinNetwork) {
                wallet.bitcoinCoins
                    .filter { networkFilter == null || it.network == networkFilter }
                    .forEach { jobs.add(launch { syncBitcoin(walletId, it) }) }
            }

            // EVM: Only if no filter or Ethereum signal
            if (networkFilter == null || networkFilter is EthereumNetwork) {
                wallet.evmTokens
                    .filter { networkFilter == null || it.network == networkFilter }
                    .forEach { token ->
                        jobs.add(launch {
                            syncEVM(walletId, token)
                        })
                    }
            }

            // Solana: Only if no filter or Solana signal
            if (networkFilter == null || networkFilter is SolanaNetwork) {
                wallet.solanaCoins
                    .filter { networkFilter == null || it.network == networkFilter }
                    .forEach { jobs.add(launch { syncSolana(walletId, it) }) }
            }

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
                bitcoinTransactionRepository.replaceTransactions(
                    walletId = walletId,
                    network = coin.network,
                    transactions = result.data
                )
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error syncing Bitcoin for ${coin.network}", e)
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
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error syncing EVM for ${token.network}", e)
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
                solanaTransactionRepository.replaceTransactions(
                    walletId = walletId,
                    network = coin.network,
                    transactions = result.data
                )
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error syncing Solana for ${coin.network}", e)
        }
    }

    companion object {
        private const val TAG = "GetAllTransactionsUC"
    }
}
