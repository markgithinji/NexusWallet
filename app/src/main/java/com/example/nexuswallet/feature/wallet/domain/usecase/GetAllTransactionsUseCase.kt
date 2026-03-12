package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTransaction
import com.example.nexuswallet.feature.ethereum.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
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

    // Fetches from network and returns combined list
    suspend operator fun invoke(walletId: String): List<Any> {
        logger.d(tag, "Fetching all transactions for wallet: $walletId")

        // This will do network calls and save to DB
        refreshTransactions(walletId)

        // Then return cached transactions
        return getCachedTransactions(walletId)
    }

    // Only gets from local DB, no network calls
    suspend fun getCachedTransactions(walletId: String): List<Any> {
        logger.d(tag, "Getting cached transactions for wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId) ?: return emptyList()
        val allTransactions = mutableListOf<Any>()

        // Bitcoin transactions from local DB only
        wallet.bitcoinCoins.forEach { coin ->
            val networkStr = when (coin.network) {
                BitcoinNetwork.Mainnet -> BitcoinNetwork.Mainnet.name
                BitcoinNetwork.Testnet -> BitcoinNetwork.Testnet.name
            }
            val transactions =
                bitcoinTransactionRepository.getTransactionsSync(walletId, networkStr)
            allTransactions.addAll(transactions)
        }

        // EVM transactions from local DB only
        val nativeTransactions = evmTransactionRepository.getNativeTransactionsSync(walletId)
        allTransactions.addAll(nativeTransactions)

        val allEvmTransactions = evmTransactionRepository.getTransactionsSync(walletId)
        allTransactions.addAll(allEvmTransactions)

        // Solana transactions from local DB only
        wallet.solanaCoins.forEach { coin ->
            val networkStr = when (coin.network) {
                SolanaNetwork.Mainnet -> SolanaNetwork.Mainnet.name
                SolanaNetwork.Devnet -> SolanaNetwork.Devnet.name
            }
            val nativeTransactions =
                solanaTransactionRepository.getNativeTransactionsSync(walletId, networkStr)
            val tokenTransactions =
                solanaTransactionRepository.getTransactionsSync(walletId, networkStr)
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

    // Only does network calls, doesn't return data
    suspend fun refreshTransactions(walletId: String) {
        logger.d(tag, "Refreshing transactions for wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId) ?: return

        // Fetch Bitcoin transactions
        wallet.bitcoinCoins.forEach { coin ->
            fetchBitcoinTransactions(walletId, coin)
        }

        // Fetch EVM transactions
        val evmAddresses = wallet.evmTokens.map { it.address }.distinct()
        evmAddresses.forEach { address ->
            val tokensByNetwork = wallet.evmTokens.filter { it.address == address }.groupBy { it.network }

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

    fun observeTransactions(walletId: String): Flow<List<Any>> {
        logger.d(tag, "Setting up transaction observation for wallet: $walletId")

        return combine(
            bitcoinTransactionRepository.getTransactions(walletId, BitcoinNetwork.Mainnet.name),
            bitcoinTransactionRepository.getTransactions(walletId, BitcoinNetwork.Testnet.name),
            evmTransactionRepository.getTransactions(walletId),
            solanaTransactionRepository.getTransactions(walletId, SolanaNetwork.Mainnet.name),
            solanaTransactionRepository.getTransactions(walletId, SolanaNetwork.Devnet.name)
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

    // ============ BITCOIN ============

    private suspend fun fetchBitcoinTransactions(walletId: String, coin: BitcoinCoin) {
        logger.d(tag, "Fetching Bitcoin transactions for ${coin.address} on ${coin.network}")

        val result = bitcoinBlockchainRepository.getAddressTransactions(
            walletId = walletId,
            address = coin.address,
            network = coin.network
        )

        when (result) {
            is Result.Success -> {
                // Delete old transactions for this network first
                val networkStr = when (coin.network) {
                    BitcoinNetwork.Mainnet -> BitcoinNetwork.Mainnet.name
                    BitcoinNetwork.Testnet -> BitcoinNetwork.Testnet.name
                }
                bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, networkStr)

                // Save transactions directly - they're already BitcoinTransaction domain models
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
        logger.d(tag, "Fetching native ETH transactions for $address on ${network.chainId}")

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
        logger.d(tag, "Fetching token transactions for ${token.symbol} at ${token.contractAddress}")

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
        logger.d(tag, "Fetching Solana transactions for ${coin.address} on ${coin.network}")

        val result = solanaBlockchainRepository.getTransactions(
            walletId = walletId,
            address = coin.address,
            network = coin.network,
            limit = 50
        )

        when (result) {
            is Result.Success -> {
                val networkStr = when (coin.network) {
                    SolanaNetwork.Mainnet -> SolanaNetwork.Mainnet.name
                    SolanaNetwork.Devnet -> SolanaNetwork.Devnet.name
                }

                // Delete old transactions
                solanaTransactionRepository.deleteForWalletAndNetwork(walletId, networkStr)

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