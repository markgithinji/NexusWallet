package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.EVMTransaction
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
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
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.flow.flowOn

@Singleton
class GetAllTransactionsUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger
) : GetAllTransactionsUseCase {

    private val tag = "GetAllTransactionsUC"

    override suspend operator fun invoke(walletId: String): List<Any> {
        logger.d(tag, "Fetching all transactions for wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId) ?: return emptyList()
        val allTransactions = mutableListOf<Any>()

        var btcMainnetCount = 0
        var btcTestnetCount = 0
        var evmNativeCount = 0
        var evmTokenCount = 0
        var solMainnetCount = 0
        var solDevnetCount = 0

        // Fetch and collect Bitcoin transactions
        wallet.bitcoinCoins.forEach { coin ->
            fetchBitcoinTransactions(walletId, coin)

            // Then read from local DB using SYNC method
            val networkStr = when (coin.network) {
                BitcoinNetwork.Mainnet -> "mainnet"
                BitcoinNetwork.Testnet -> "testnet"
            }
            val transactions = bitcoinTransactionRepository.getTransactionsSync(walletId, networkStr)
            allTransactions.addAll(transactions)

            if (coin.network == BitcoinNetwork.Mainnet) {
                btcMainnetCount += transactions.size
            } else {
                btcTestnetCount += transactions.size
            }
        }

        // Fetch EVM transactions (native + tokens)
        val evmAddresses = wallet.evmTokens.map { it.address }.distinct()
        evmAddresses.forEach { address ->
            // Fetch native ETH transactions for each network
            val tokensByNetwork = wallet.evmTokens.filter { it.address == address }.groupBy { it.network }

            tokensByNetwork.forEach { (network, tokens) ->
                val nativeToken = tokens.find { it is NativeETH }
                fetchEVMNativeTransactions(walletId, address, network, nativeToken?.externalId)

                // Fetch token transactions for each token on this network
                tokens.filter { it.contractAddress != "0x0000000000000000000000000000000000000000" }
                    .forEach { token ->
                        fetchEVMTokenTransactions(walletId, address, token)
                    }
            }

            // Then read from local DB using SYNC methods
            val nativeTransactions = evmTransactionRepository.getNativeTransactionsSync(walletId)
            evmNativeCount += nativeTransactions.size
            allTransactions.addAll(nativeTransactions)

            val allEvmTransactions = evmTransactionRepository.getTransactionsSync(walletId)
            evmTokenCount += allEvmTransactions.size - nativeTransactions.size
            allTransactions.addAll(allEvmTransactions)
        }

        // Fetch Solana transactions
        wallet.solanaCoins.forEach { coin ->
            fetchSolanaTransactions(walletId, coin)

            // Then read from local DB using SYNC methods
            val networkStr = when (coin.network) {
                SolanaNetwork.Mainnet -> "mainnet"
                SolanaNetwork.Devnet -> "devnet"
            }
            val nativeTransactions = solanaTransactionRepository.getNativeTransactionsSync(walletId, networkStr)
            val tokenTransactions = solanaTransactionRepository.getTransactionsSync(walletId, networkStr)

            allTransactions.addAll(nativeTransactions)
            allTransactions.addAll(tokenTransactions)

            if (coin.network == SolanaNetwork.Mainnet) {
                solMainnetCount += nativeTransactions.size + tokenTransactions.size
            } else {
                solDevnetCount += nativeTransactions.size + tokenTransactions.size
            }
        }

        // Sort all transactions by timestamp descending (newest first)
        val sortedTransactions = allTransactions.sortedByDescending { transaction ->
            when (transaction) {
                is BitcoinTransaction -> transaction.timestamp
                is EVMTransaction -> transaction.timestamp
                is SolanaTransaction -> transaction.timestamp
                else -> 0L
            }
        }

        logger.d(tag, "Retrieved transactions - BTC Mainnet: $btcMainnetCount, BTC Testnet: $btcTestnetCount, EVM: ${evmNativeCount + evmTokenCount} (Native: $evmNativeCount, Token: $evmTokenCount), SOL Mainnet: $solMainnetCount, SOL Devnet: $solDevnetCount")
        logger.d(tag, "Returning ${sortedTransactions.size} total transactions")

        return sortedTransactions
    }

    override fun observeTransactions(walletId: String): Flow<List<Any>> {
        logger.d(tag, "Setting up transaction observation for wallet: $walletId")

        return combine(
            bitcoinTransactionRepository.getTransactions(walletId, "mainnet"),
            bitcoinTransactionRepository.getTransactions(walletId, "testnet"),
            evmTransactionRepository.getTransactions(walletId),
            solanaTransactionRepository.getTransactions(walletId, "mainnet"),
            solanaTransactionRepository.getTransactions(walletId, "devnet")
        ) { btcMainnet, btcTestnet, evm, solMainnet, solDevnet ->
            val allTransactions = mutableListOf<Any>()

            // Add ALL transaction types
            allTransactions.addAll(btcMainnet)
            allTransactions.addAll(btcTestnet)
            allTransactions.addAll(evm)
            allTransactions.addAll(solMainnet)
            allTransactions.addAll(solDevnet)

            // Sort by timestamp descending (newest first)
            allTransactions.sortedByDescending { transaction ->
                when (transaction) {
                    is BitcoinTransaction -> transaction.timestamp
                    is EVMTransaction -> transaction.timestamp
                    is SolanaTransaction -> transaction.timestamp
                    else -> 0L
                }
            }.also { sortedList ->
                // Add debug logging to verify what's being emitted
                val btcCount = sortedList.count { it is BitcoinTransaction }
                val evmCount = sortedList.count { it is EVMTransaction }
                val solCount = sortedList.count { it is SolanaTransaction }
                logger.d(tag, "Observing - BTC: $btcCount, EVM: $evmCount, SOL: $solCount, Total: ${sortedList.size}")
            }
        }.flowOn(Dispatchers.IO)
    }

    // ============ BITCOIN ============

    private suspend fun fetchBitcoinTransactions(walletId: String, coin: BitcoinCoin) {
        try {
            logger.d(tag, "Fetching Bitcoin transactions for ${coin.address} on ${coin.network}")

            val result = bitcoinBlockchainRepository.getAddressTransactions(
                address = coin.address,
                network = coin.network
            )

            when (result) {
                is Result.Success -> {
                    val transactions = result.data.map { dto ->
                        BitcoinTransaction(
                            id = dto.txid,
                            walletId = walletId,
                            fromAddress = dto.fromAddress,
                            toAddress = dto.toAddress,
                            status = dto.status,
                            timestamp = dto.timestamp * 1000, // Convert to milliseconds
                            note = null,
                            feeLevel = FeeLevel.NORMAL, // Default, we don't know the fee level from API
                            amountSatoshis = dto.amount,
                            amountBtc = (dto.amount.toDouble() / 100_000_000).toString(),
                            feeSatoshis = dto.fee ?: 0,
                            feeBtc = ((dto.fee?.toDouble() ?: 0.0) / 100_000_000).toString(),
                            feePerByte = 0.0, // Not available from basic API
                            estimatedSize = 0,
                            signedHex = null,
                            txHash = dto.txid,
                            network = coin.network,
                            isIncoming = dto.isIncoming
                        )
                    }

                    transactions.forEach { transaction ->
                        bitcoinTransactionRepository.saveTransaction(transaction)
                    }

                    logger.d(tag, "Saved ${transactions.size} Bitcoin transactions for ${coin.address}")
                }
                is Result.Error -> {
                    logger.e(tag, "Failed to fetch Bitcoin transactions: ${result.message}")
                }
                else -> {}
            }
        } catch (e: Exception) {
            logger.e(tag, "Exception fetching Bitcoin transactions", e)
        }
    }

    // ============ EVM ============

    private suspend fun fetchEVMNativeTransactions(
        walletId: String,
        address: String,
        network: EthereumNetwork,
        tokenExternalId: String?
    ) {
        try {
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
        } catch (e: Exception) {
            logger.e(tag, "Exception fetching native ETH transactions", e)
        }
    }

    private suspend fun fetchEVMTokenTransactions(walletId: String, address: String, token: EVMToken) {
        try {
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
        } catch (e: Exception) {
            logger.e(tag, "Exception fetching token transactions", e)
        }
    }

    // ============ SOLANA ============

    private suspend fun fetchSolanaTransactions(walletId: String, coin: SolanaCoin) {
        try {
            logger.d(tag, "Fetching Solana transactions for ${coin.address} on ${coin.network}")

            // Get full transaction history
            val result = solanaBlockchainRepository.getFullTransactionHistory(
                address = coin.address,
                network = coin.network,
                limit = 50 // Fetch last 50 transactions
            )

            when (result) {
                is Result.Success -> {
                    val transactions = result.data.mapNotNull { (sigInfo, details) ->
                        if (details == null) return@mapNotNull null

                        val transferInfo = solanaBlockchainRepository.parseTransferFromDetails(
                            details = details,
                            walletAddress = coin.address
                        ) ?: return@mapNotNull null

                        // Check if this is a token transfer or native SOL
                        val isTokenTransfer = details.transaction.message.accountKeys.any { key ->
                            coin.splTokens.any { it.mintAddress == key }
                        }

                        if (isTokenTransfer) {
                            // TODO: Handle SPL token transactions
                            // For now, we'll skip token transactions
                            null
                        } else {
                            // Native SOL transfer
                            SolanaTransaction(
                                id = sigInfo.signature,
                                walletId = walletId,
                                fromAddress = transferInfo.from,
                                toAddress = transferInfo.to,
                                status = if (sigInfo.confirmationStatus == "finalized")
                                    TransactionStatus.SUCCESS
                                else
                                    TransactionStatus.PENDING,
                                timestamp = (sigInfo.blockTime ?: 0) * 1000,
                                note = null,
                                feeLevel = FeeLevel.NORMAL, // Default
                                amountLamports = transferInfo.amount,
                                amountSol = (transferInfo.amount.toDouble() / 1_000_000_000).toString(),
                                feeLamports = transferInfo.fee,
                                feeSol = (transferInfo.fee.toDouble() / 1_000_000_000).toString(),
                                signature = sigInfo.signature,
                                network = coin.network,
                                isIncoming = transferInfo.isIncoming,
                                tokenMint = null,
                                tokenSymbol = null,
                                tokenDecimals = null,
                                slot = sigInfo.slot,
                                blockTime = sigInfo.blockTime
                            )
                        }
                    }

                    transactions.forEach { transaction ->
                        solanaTransactionRepository.saveTransaction(transaction)
                    }

                    logger.d(tag, "Saved ${transactions.size} Solana transactions for ${coin.address}")
                }
                is Result.Error -> {
                    logger.e(tag, "Failed to fetch Solana transactions: ${result.message}")
                }
                else -> {}
            }
        } catch (e: Exception) {
            logger.e(tag, "Exception fetching Solana transactions", e)
        }
    }
}