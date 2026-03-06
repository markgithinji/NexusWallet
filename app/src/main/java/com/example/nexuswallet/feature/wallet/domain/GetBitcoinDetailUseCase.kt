package com.example.nexuswallet.feature.wallet.domain

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.SyncBitcoinTransactionsUseCase
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.SyncEthereumTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SyncSolanaTransactionsUseCase
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.toDomain

interface GetBitcoinDetailUseCase {
    suspend operator fun invoke(
        walletId: String,
        network: String = ""
    ): Result<BitcoinDetailResult>
}

@Singleton
class GetBitcoinDetailUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
    private val logger: Logger
) : GetBitcoinDetailUseCase {

    private val tag = "GetBitcoinDetailUC"

    override suspend operator fun invoke(
        walletId: String,
        network: String
    ): Result<BitcoinDetailResult> {
        return try {
            logger.d(tag, "Getting Bitcoin details for wallet: $walletId, network: $network")

            // 1. Get wallet
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found")

            // 2. Find the specific Bitcoin coin
            val bitcoinCoin = wallet.bitcoinCoins.find {
                when (network.lowercase()) {
                    "mainnet" -> it.network == BitcoinNetwork.Mainnet
                    "testnet" -> it.network == BitcoinNetwork.Testnet
                    else -> true
                }
            } ?: wallet.bitcoinCoins.firstOrNull()
            ?: return Result.Error("Bitcoin not enabled")

            // 3. Fetch fresh transactions from blockchain
            val networkParam = when (bitcoinCoin.network) {
                BitcoinNetwork.Mainnet -> "mainnet"
                BitcoinNetwork.Testnet -> "testnet"
            }

            val txResult = bitcoinBlockchainRepository.getAddressTransactions(
                address = bitcoinCoin.address,
                network = bitcoinCoin.network
            )

            if (txResult is Result.Success) {
                // Delete old transactions and save new ones
                bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, networkParam)

                txResult.data.forEach { tx ->
                    val domainTx = tx.toDomain(
                        walletId = walletId,
                        isIncoming = tx.isIncoming,
                        network = bitcoinCoin.network
                    )
                    bitcoinTransactionRepository.saveTransaction(domainTx)
                }
                logger.d(tag, "Synced ${txResult.data.size} transactions")
            }

            // 4. Get balance
            val balance = walletRepository.getWalletBalance(walletId)
            val networkKey = when (bitcoinCoin.network) {
                BitcoinNetwork.Mainnet -> "mainnet"
                BitcoinNetwork.Testnet -> "testnet"
            }
            val coinBalance = balance?.bitcoinBalances?.get(networkKey)

            // 5. Get transactions from local DB
            val transactions = bitcoinTransactionRepository.getTransactionsSync(walletId, networkParam)
            val displayTransactions = formatTransactionDisplayUseCase.formatTransactionList(
                transactions,
                CoinType.BITCOIN
            )

            val result = BitcoinDetailResult(
                walletId = walletId,
                address = bitcoinCoin.address,
                balance = coinBalance?.btc ?: "0",
                balanceFormatted = "${coinBalance?.btc ?: "0"} BTC",
                usdValue = coinBalance?.usdValue ?: 0.0,
                network = bitcoinCoin.network.name,
                networkDisplayName = if (bitcoinCoin.network == BitcoinNetwork.Mainnet) "Mainnet" else "Testnet",
                transactions = displayTransactions,
                bitcoinCoin = bitcoinCoin,
                availableNetworks = wallet.bitcoinCoins.map { it.network }
            )

            logger.d(tag, "Successfully retrieved Bitcoin details")
            Result.Success(result)

        } catch (e: Exception) {
            logger.e(tag, "Error getting Bitcoin details", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }
}