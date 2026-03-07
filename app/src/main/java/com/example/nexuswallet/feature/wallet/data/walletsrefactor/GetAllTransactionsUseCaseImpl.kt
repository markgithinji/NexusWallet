package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.EVMTransaction
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton
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

    // Fetches from network and returns combined list
    override suspend operator fun invoke(walletId: String): List<Any> {
        logger.d(tag, "Fetching all transactions for wallet: $walletId")

        // This will do network calls and save to DB
        refreshTransactions(walletId)

        // Then return cached transactions
        return getCachedTransactions(walletId)
    }

    // Only gets from local DB, no network calls
    override suspend fun getCachedTransactions(walletId: String): List<Any> {
        logger.d(tag, "Getting cached transactions for wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId) ?: return emptyList()
        val allTransactions = mutableListOf<Any>()

        // Bitcoin transactions from local DB only
        wallet.bitcoinCoins.forEach { coin ->
            val networkStr = when (coin.network) {
                BitcoinNetwork.Mainnet -> BitcoinNetwork.Mainnet.name
                BitcoinNetwork.Testnet -> BitcoinNetwork.Testnet.name
            }
            val transactions = bitcoinTransactionRepository.getTransactionsSync(walletId, networkStr)
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
            val nativeTransactions = solanaTransactionRepository.getNativeTransactionsSync(walletId, networkStr)
            val tokenTransactions = solanaTransactionRepository.getTransactionsSync(walletId, networkStr)
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
    override suspend fun refreshTransactions(walletId: String) {
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

                tokens.filter { it.contractAddress != "0x0000000000000000000000000000000000000000" }
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

    override fun observeTransactions(walletId: String): Flow<List<Any>> {
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
                    // Delete old transactions for this network first
                    val networkStr = when (coin.network) {
                        BitcoinNetwork.Mainnet -> BitcoinNetwork.Mainnet.name
                        BitcoinNetwork.Testnet -> BitcoinNetwork.Testnet.name
                    }
                    bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, networkStr)

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
                    // Delete old transactions for this network first
                    val networkStr = when (coin.network) {
                        SolanaNetwork.Mainnet -> SolanaNetwork.Mainnet.name
                        SolanaNetwork.Devnet -> SolanaNetwork.Devnet.name
                    }
                    solanaTransactionRepository.deleteForWalletAndNetwork(walletId, networkStr)

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