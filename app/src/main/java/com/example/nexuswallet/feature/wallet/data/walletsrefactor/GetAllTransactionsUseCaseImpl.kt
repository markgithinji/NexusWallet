package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.EVMTransaction
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.GetAllTransactionsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllTransactionsUseCaseImpl @Inject constructor(
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val logger: Logger
) : GetAllTransactionsUseCase {

    private val tag = "GetAllTransactionsUC"

    override suspend fun invoke(walletId: String): List<Any> = withContext(Dispatchers.IO) {
        logger.d(tag, "Fetching all transactions for wallet: $walletId")
        val startTime = System.currentTimeMillis()

        coroutineScope {
            val bitcoinMainnetDeferred = async {
                bitcoinTransactionRepository.getTransactions(walletId, "mainnet").firstOrNull() ?: emptyList()
            }
            val bitcoinTestnetDeferred = async {
                bitcoinTransactionRepository.getTransactions(walletId, "testnet").firstOrNull() ?: emptyList()
            }
            val evmDeferred = async {
                evmTransactionRepository.getTransactions(walletId).firstOrNull() ?: emptyList()
            }
            val solanaMainnetDeferred = async {
                solanaTransactionRepository.getTransactions(walletId, "mainnet").firstOrNull() ?: emptyList()
            }
            val solanaDevnetDeferred = async {
                solanaTransactionRepository.getTransactions(walletId, "devnet").firstOrNull() ?: emptyList()
            }

            val bitcoinMainnet = bitcoinMainnetDeferred.await()
            val bitcoinTestnet = bitcoinTestnetDeferred.await()
            val evmTxs = evmDeferred.await()
            val solanaMainnet = solanaMainnetDeferred.await()
            val solanaDevnet = solanaDevnetDeferred.await()

            val bitcoinMainnetCount = bitcoinMainnet.size
            val bitcoinTestnetCount = bitcoinTestnet.size
            val evmCount = evmTxs.size
            val solanaMainnetCount = solanaMainnet.size
            val solanaDevnetCount = solanaDevnet.size

            // Count token types for logging
            val nativeCount = evmTxs.count { it is NativeETHTransaction }
            val tokenCount = evmTxs.count { it is TokenTransaction }

            logger.d(
                tag,
                "Retrieved transactions - BTC Mainnet: $bitcoinMainnetCount, BTC Testnet: $bitcoinTestnetCount, " +
                        "EVM: $evmCount (Native: $nativeCount, Token: $tokenCount), " +
                        "SOL Mainnet: $solanaMainnetCount, SOL Devnet: $solanaDevnetCount"
            )

            // Combine all transactions into a List<Any>
            val allTransactions: List<Any> = bitcoinMainnet + bitcoinTestnet + evmTxs + solanaMainnet + solanaDevnet

            val sortedTransactions = allTransactions.sortedByDescending { transaction ->
                when (transaction) {
                    is BitcoinTransaction -> transaction.timestamp
                    is NativeETHTransaction -> transaction.timestamp
                    is TokenTransaction -> transaction.timestamp
                    is SolanaTransaction -> transaction.timestamp
                    else -> 0L  // Keep else branch for safety with Any type
                }
            }

            val duration = System.currentTimeMillis() - startTime
            logger.d(tag, "Returning ${sortedTransactions.size} total transactions in ${duration}ms")

            sortedTransactions
        }
    }

    override fun observeTransactions(walletId: String): Flow<List<Any>> = combine(
        // Bitcoin - both networks
        bitcoinTransactionRepository.getTransactions(walletId, "mainnet"),
        bitcoinTransactionRepository.getTransactions(walletId, "testnet"),

        // EVM - all tokens
        evmTransactionRepository.getTransactions(walletId),

        // Solana - both networks
        solanaTransactionRepository.getTransactions(walletId, "mainnet"),
        solanaTransactionRepository.getTransactions(walletId, "devnet")
    ) { bitcoinMainnet, bitcoinTestnet, evmTxs, solanaMainnet, solanaDevnet ->

        val evmCount = evmTxs.size
        val nativeCount = evmTxs.count { it is NativeETHTransaction }
        val tokenCount = evmTxs.count { it is TokenTransaction }

        val combined = (bitcoinMainnet + bitcoinTestnet + evmTxs + solanaMainnet + solanaDevnet)
            .sortedByDescending { transaction ->
                when (transaction) {
                    is BitcoinTransaction -> transaction.timestamp
                    is NativeETHTransaction -> transaction.timestamp
                    is TokenTransaction -> transaction.timestamp
                    is SolanaTransaction -> transaction.timestamp
                    else -> 0L
                }
            }

        logger.d(
            tag,
            "Observing wallet $walletId - BTC Mainnet: ${bitcoinMainnet.size}, BTC Testnet: ${bitcoinTestnet.size}, " +
                    "EVM: $evmCount (Native: $nativeCount, Token: $tokenCount), " +
                    "SOL Mainnet: ${solanaMainnet.size}, SOL Devnet: ${solanaDevnet.size}, " +
                    "Total: ${combined.size}"
        )

        combined
    }

    override suspend fun getBitcoinTransactions(walletId: String, network: String): List<BitcoinTransaction> = withContext(Dispatchers.IO) {
        bitcoinTransactionRepository.getTransactions(walletId, network).firstOrNull() ?: emptyList()
    }

    override fun observeBitcoinTransactions(walletId: String, network: String): Flow<List<BitcoinTransaction>> {
        return bitcoinTransactionRepository.getTransactions(walletId, network)
    }

    override suspend fun getEVMTokenTransactions(walletId: String, tokenExternalId: String): List<EVMTransaction> = withContext(Dispatchers.IO) {
        evmTransactionRepository.getTransactionsByTokenExternalId(walletId, tokenExternalId).firstOrNull() ?: emptyList()
    }

    override fun observeEVMTokenTransactions(walletId: String, tokenExternalId: String): Flow<List<EVMTransaction>> {
        return evmTransactionRepository.getTransactionsByTokenExternalId(walletId, tokenExternalId)
    }

    override suspend fun getSolanaTransactions(walletId: String, network: String): List<SolanaTransaction> = withContext(Dispatchers.IO) {
        solanaTransactionRepository.getTransactions(walletId, network).firstOrNull() ?: emptyList()
    }

    override fun observeSolanaTransactions(walletId: String, network: String): Flow<List<SolanaTransaction>> {
        return solanaTransactionRepository.getTransactions(walletId, network)
    }
}