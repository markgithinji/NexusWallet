package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinDetailResult
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.core.util.Result

@Singleton
class GetBitcoinDetailUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger
) {

    private val tag = "GetBitcoinDetailUC"

    suspend operator fun invoke(
        walletId: String,
        network: BitcoinNetwork
    ): Result<BitcoinDetailResult> {
        return try {
            logger.d(tag, "Getting Bitcoin details for wallet: $walletId, network: $network")

            // 1. Get wallet
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found")

            // 2. Find the specific Bitcoin coin
            val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
                ?: wallet.bitcoinCoins.firstOrNull()
                ?: return Result.Error("Bitcoin not enabled for $network")

            // 3. Fetch fresh transactions from blockchain
            val networkParam = bitcoinCoin.network.name

            logger.d(tag, "Using networkParam: $networkParam for ${bitcoinCoin.network}")

            val txResult = bitcoinBlockchainRepository.getAddressTransactions(
                walletId = walletId,
                address = bitcoinCoin.address,
                network = bitcoinCoin.network
            )

            if (txResult is Result.Success) {
                logger.d(tag, "Fetched ${txResult.data.size} transactions from blockchain")

                // Delete old transactions and save new ones
                bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, networkParam)
                logger.d(tag, "Deleted old transactions for wallet $walletId, network $networkParam")

                // Save transactions directly
                txResult.data.forEachIndexed { index, tx ->
                    bitcoinTransactionRepository.saveTransaction(tx)
                    logger.d(tag, "Saved transaction $index: ${tx.txHash?.take(8) ?: "unknown"} with network ${bitcoinCoin.network.name}")
                }
                logger.d(tag, "Synced ${txResult.data.size} transactions")
            }

            // 4. Get balance
            val balance = walletRepository.getWalletBalance(walletId)
            val coinBalance = balance?.bitcoinBalances?.get(bitcoinCoin.network)
            logger.d(tag, "Balance for ${bitcoinCoin.network}: ${coinBalance?.btc ?: "0"} BTC")

            // 5. Get raw transactions from local DB
            logger.d(tag, "Querying transactions with walletId=$walletId, network=$networkParam")
            val rawTransactions = bitcoinTransactionRepository.getTransactionsSync(walletId, networkParam)
            logger.d(tag, "Retrieved ${rawTransactions.size} raw transactions from DB for $networkParam")

            val result = BitcoinDetailResult(
                walletId = walletId,
                address = bitcoinCoin.address,
                balance = coinBalance?.btc ?: "0",
                balanceFormatted = "${coinBalance?.btc ?: "0"} BTC",
                usdValue = coinBalance?.usdValue ?: 0.0,
                network = bitcoinCoin.network.name,
                networkDisplayName = if (bitcoinCoin.network == BitcoinNetwork.Mainnet) "Mainnet" else "Testnet",
                rawTransactions = rawTransactions,
                bitcoinCoin = bitcoinCoin,
                availableNetworks = wallet.bitcoinCoins.map { it.network }
            )

            logger.d(tag, "Successfully retrieved Bitcoin details with ${rawTransactions.size} raw transactions")
            Result.Success(result)

        } catch (e: Exception) {
            logger.e(tag, "Error getting Bitcoin details", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }
}