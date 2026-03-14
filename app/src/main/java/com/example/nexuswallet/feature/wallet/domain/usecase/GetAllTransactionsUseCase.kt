package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTransaction
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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

    suspend operator fun invoke(
        walletId: String,
        forceRefresh: Boolean = false,
        observe: Boolean = false
    ): Flow<List<Any>>? {
        logger.d(tag, "Getting all transactions for wallet: $walletId, forceRefresh: $forceRefresh, observe: $observe")

        return if (observe) {
            // Return flow for observation
            createObservationFlow(walletId)
        } else {
            // One-time fetch
            fetchTransactions(walletId, forceRefresh)
            null
        }
    }

    private suspend fun fetchTransactions(walletId: String, forceRefresh: Boolean): List<Any> {
        if (forceRefresh) {
            refreshTransactions(walletId)
        }
        return getCachedTransactions(walletId)
    }

    private fun createObservationFlow(walletId: String): Flow<List<Any>> {
        logger.d(tag, "Creating transaction observation flow for wallet: $walletId")

        return combine(
            bitcoinTransactionRepository.getTransactions(walletId, BitcoinNetwork.Mainnet),
            bitcoinTransactionRepository.getTransactions(walletId, BitcoinNetwork.Testnet),
            evmTransactionRepository.getTransactions(walletId),
            solanaTransactionRepository.getTransactions(walletId, SolanaNetwork.Mainnet),
            solanaTransactionRepository.getTransactions(walletId, SolanaNetwork.Devnet)
        ) { btcMainnet, btcTestnet, evm, solMainnet, solDevnet ->
            val allTransactions = mutableListOf<Any>()

            allTransactions.addAll(btcMainnet)
            allTransactions.addAll(btcTestnet)
            allTransactions.addAll(evm)
            allTransactions.addAll(solMainnet)
            allTransactions.addAll(solDevnet)

            allTransactions.sortedByDescending { transaction ->
                when (transaction) {
                    is BitcoinTransaction -> transaction.timestamp
                    is EVMTransaction -> transaction.timestamp
                    is SolanaTransaction -> transaction.timestamp
                    else -> 0L
                }
            }.also { sortedList ->
                val btcCount = sortedList.count { it is BitcoinTransaction }
                val evmCount = sortedList.count { it is EVMTransaction }
                val solCount = sortedList.count { it is SolanaTransaction }
                logger.d(
                    tag,
                    "Observing - BTC: $btcCount, EVM: $evmCount, SOL: $solCount, Total: ${sortedList.size}"
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun getCachedTransactions(walletId: String): List<Any> {
        logger.d(tag, "Getting cached transactions for wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId) ?: return emptyList()
        val allTransactions = mutableListOf<Any>()

        // Bitcoin transactions from local DB only
        wallet.bitcoinCoins.forEach { coin ->
            val transactions =
                bitcoinTransactionRepository.getTransactionsSync(walletId, coin.network)
            allTransactions.addAll(transactions)
        }

        // EVM transactions from local DB only
        val nativeTransactions = evmTransactionRepository.getNativeTransactionsSync(walletId)
        allTransactions.addAll(nativeTransactions)

        val allEvmTransactions = evmTransactionRepository.getTransactionsSync(walletId)
        allTransactions.addAll(allEvmTransactions)

        // Solana transactions from local DB only
        wallet.solanaCoins.forEach { coin ->
            val nativeTransactions =
                solanaTransactionRepository.getNativeTransactionsSync(walletId, coin.network)
            val tokenTransactions =
                solanaTransactionRepository.getTransactionsSync(walletId, coin.network)
            allTransactions.addAll(nativeTransactions)
            allTransactions.addAll(tokenTransactions)
        }

        // Sort by timestamp descending
        return allTransactions.sortedByDescending { transaction ->
            when (transaction) {
                is BitcoinTransaction -> transaction.timestamp
                is EVMTransaction -> transaction.timestamp
                is SolanaTransaction -> transaction.timestamp
                else -> 0L
            }
        }
    }

    private suspend fun refreshTransactions(walletId: String) {
        logger.d(tag, "Refreshing transactions for wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId) ?: return

        // Fetch Bitcoin transactions
        wallet.bitcoinCoins.forEach { coin ->
            fetchBitcoinTransactions(walletId, coin)
        }

        // Fetch EVM transactions
        val evmAddresses = wallet.evmTokens.map { it.address }.distinct()
        evmAddresses.forEach { address ->
            val tokensByNetwork =
                wallet.evmTokens.filter { it.address == address }.groupBy { it.network }

            tokensByNetwork.forEach { (network, tokens) ->
                val nativeToken = tokens.find { it is NativeETH }
                fetchEVMNativeTransactions(walletId, address, network, nativeToken?.externalId)

                // Fetch all non-native tokens (USDC, USDT, ERC20)
                tokens.filter { it !is NativeETH }
                    .forEach { token ->
                        fetchEVMTokenTransactions(walletId, address, token)
                    }
            }
        }

        // Fetch Solana transactions
        wallet.solanaCoins.forEach { coin ->
            fetchSolanaTransactions(walletId, coin)
        }

        logger.d(tag, "Transaction refresh completed")
    }

    // ============ BITCOIN ============

    private suspend fun fetchBitcoinTransactions(walletId: String, coin: BitcoinCoin) {
        logger.d(tag, "Fetching Bitcoin transactions for ${coin.address} on ${coin.network.displayName}")

        val result = bitcoinBlockchainRepository.getAddressTransactions(
            walletId = walletId,
            address = coin.address,
            network = coin.network
        )

        when (result) {
            is Result.Success -> {
                // Delete old transactions for this network first
                bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, coin.network)

                // Save transactions directly
                result.data.forEach { transaction ->
                    bitcoinTransactionRepository.saveTransaction(transaction)
                }

                logger.d(tag, "Saved ${result.data.size} Bitcoin transactions for ${coin.address}")
            }

            is Result.Error -> {
                logger.e(tag, "Failed to fetch Bitcoin transactions: ${result.message}")
            }

            else -> {}
        }
    }

    // ============ EVM ============

    private suspend fun fetchEVMNativeTransactions(
        walletId: String,
        address: String,
        network: EthereumNetwork,
        tokenExternalId: String?
    ) {
        logger.d(tag, "Fetching native ETH transactions for $address on ${network.displayName}")

        val result = evmBlockchainRepository.getNativeTransactions(
            address = address,
            network = network,
            walletId = walletId,
            tokenExternalId = tokenExternalId
        )

        when (result) {
            is Result.Success -> {
                result.data.forEach { transaction ->
                    evmTransactionRepository.saveTransaction(transaction)
                }
                logger.d(tag, "Saved ${result.data.size} native ETH transactions for $address")
            }

            is Result.Error -> {
                logger.e(tag, "Failed to fetch native ETH transactions: ${result.message}")
            }

            else -> {}
        }
    }

    private suspend fun fetchEVMTokenTransactions(
        walletId: String,
        address: String,
        token: EVMToken
    ) {
        logger.d(tag, "Fetching token transactions for ${token.symbol} on ${token.network.displayName}")

        val result = evmBlockchainRepository.getTokenTransactions(
            address = address,
            tokenContract = token.contractAddress,
            network = token.network,
            walletId = walletId,
            tokenExternalId = token.externalId
        )

        when (result) {
            is Result.Success -> {
                result.data.forEach { transaction ->
                    evmTransactionRepository.saveTransaction(transaction)
                }
                logger.d(tag, "Saved ${result.data.size} ${token.symbol} transactions")
            }

            is Result.Error -> {
                logger.e(tag, "Failed to fetch ${token.symbol} transactions: ${result.message}")
            }

            else -> {}
        }
    }

    // ============ SOLANA ============

    private suspend fun fetchSolanaTransactions(walletId: String, coin: SolanaCoin) {
        logger.d(tag, "Fetching Solana transactions for ${coin.address} on ${coin.network.displayName}")

        val result = solanaBlockchainRepository.getTransactions(
            walletId = walletId,
            address = coin.address,
            network = coin.network,
            limit = 50
        )

        when (result) {
            is Result.Success -> {
                // Delete old transactions for this network
                solanaTransactionRepository.deleteForWalletAndNetwork(walletId, coin.network)

                // Save new transactions
                result.data.forEach { transaction ->
                    solanaTransactionRepository.saveTransaction(transaction)
                }

                logger.d(tag, "Saved ${result.data.size} Solana transactions for ${coin.address}")
            }

            is Result.Error -> {
                logger.e(tag, "Failed to fetch Solana transactions: ${result.message}")
            }

            else -> {}
        }
    }
}