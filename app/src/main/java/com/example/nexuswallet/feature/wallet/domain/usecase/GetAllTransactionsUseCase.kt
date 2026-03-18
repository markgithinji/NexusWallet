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
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
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
    private val logger: Logger
) {

    private val tag = "GetAllTransactionsUC"

    /**
     * Returns a reactive stream of transactions.
     * 1. Emits cached data immediately.
     * 2. Triggers a background sync as a side effect.
     * 3. Automatically emits updated data once the sync writes to the DB.
     */
    operator fun invoke(walletId: String): Flow<List<Transaction>> {
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
                // Non-blocking background sync
                syncTransactions(walletId)
            }
            .distinctUntilChanged()
    }

    /**
     * Orchestrates the remote fetch.
     * It does not return data; it only writes to repositories.
     */
    private suspend fun syncTransactions(walletId: String) = coroutineScope {
        logger.d(tag, "Starting background sync for wallet: $walletId")
        val wallet = walletRepository.getWallet(walletId) ?: return@coroutineScope

        // Run different chains in parallel
        val bitcoinJob = launch { syncBitcoin(walletId, wallet) }
        val evmJob = launch { syncEVM(walletId, wallet) }
        val solanaJob = launch { syncSolana(walletId, wallet) }

        // Wait for all to complete
        listOf(bitcoinJob, evmJob, solanaJob).joinAll()
    }

    // ============ BITCOIN SYNC ============

    private suspend fun syncBitcoin(walletId: String, wallet: Wallet) {
        wallet.bitcoinCoins.forEach { coin ->
            val result = bitcoinBlockchainRepository.getAddressTransactions(
                walletId = walletId,
                address = coin.address,
                network = coin.network
            )
            if (result is Result.Success) {
                bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, coin.network)
                result.data.forEach { bitcoinTransactionRepository.saveTransaction(it) }
            }
        }
    }

    // ============ EVM SYNC ============

    private suspend fun syncEVM(walletId: String, wallet: Wallet) {
        wallet.evmTokens.groupBy { it.address to it.network }.forEach { (key, tokens) ->
            val (address, network) = key

            // Sync Native (ETH)
            val nativeToken = tokens.find { it is NativeETH }
            val nativeRes = evmBlockchainRepository.getNativeTransactions(
                address = address,
                network = network,
                walletId = walletId,
                evmTokenType = nativeToken?.evmTokenType
            )
            if (nativeRes is Result.Success) {
                nativeRes.data.forEach { evmTransactionRepository.saveTransaction(it) }
            }

            // Sync Token Transactions (USDC, USDT)
            tokens.filter { it !is NativeETH }.forEach { token ->
                val tokenRes = evmBlockchainRepository.getTokenTransactions(
                    address = address,
                    tokenContract = token.contractAddress,
                    network = token.network,
                    walletId = walletId,
                    evmTokenType = token.evmTokenType
                )
                if (tokenRes is Result.Success) {
                    tokenRes.data.forEach { evmTransactionRepository.saveTransaction(it) }
                }
            }
        }
    }

    // ============ SOLANA SYNC ============

    private suspend fun syncSolana(walletId: String, wallet: Wallet) {
        wallet.solanaCoins.forEach { coin ->
            val result = solanaBlockchainRepository.getTransactions(
                walletId = walletId,
                address = coin.address,
                network = coin.network,
                limit = 50
            )
            if (result is Result.Success) {
                solanaTransactionRepository.deleteForWalletAndNetwork(walletId, coin.network)
                result.data.forEach { solanaTransactionRepository.saveTransaction(it) }
            }
        }
    }

    private fun logSummary(list: List<Transaction>) {
        val btcCount = list.count { it is BitcoinTransaction }
        val evmCount = list.count { it is EVMTransaction }
        val solCount = list.count { it is SolanaTransaction }
        logger.d(tag, "Reactive Update - BTC: $btcCount, EVM: $evmCount, SOL: $solCount, Total: ${list.size}")
    }
}