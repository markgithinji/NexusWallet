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
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken

interface GetEthereumDetailUseCase {
    suspend fun getEthDetails(
        walletId: String,
        network: String = ""
    ): Result<EthereumDetailResult>

    suspend fun getUsdcDetails(
        walletId: String,
        network: String = ""
    ): Result<EthereumDetailResult>
}

@Singleton
class GetEthereumDetailUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
    private val logger: Logger
) : GetEthereumDetailUseCase {

    private val tag = "GetEthereumDetailUC"

    override suspend fun getEthDetails(
        walletId: String,
        network: String
    ): Result<EthereumDetailResult> = getDetails(walletId, network, CoinType.ETHEREUM)

    override suspend fun getUsdcDetails(
        walletId: String,
        network: String
    ): Result<EthereumDetailResult> = getDetails(walletId, network, CoinType.USDC)

    private suspend fun getDetails(
        walletId: String,
        network: String,
        coinType: CoinType
    ): Result<EthereumDetailResult> {
        return try {
            logger.d(tag, "Getting $coinType details for wallet: $walletId, network: $network")

            // 1. Get wallet
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found")

            // 2. Find the specific token
            val (token, isEth) = when (coinType) {
                CoinType.ETHEREUM -> {
                    val nativeEth = wallet.evmTokens.filterIsInstance<NativeETH>().find {
                        when (network.lowercase()) {
                            "mainnet" -> it.network == EthereumNetwork.Mainnet
                            "sepolia" -> it.network == EthereumNetwork.Sepolia
                            else -> true
                        }
                    } ?: wallet.evmTokens.filterIsInstance<NativeETH>().firstOrNull()
                    ?: return Result.Error("Ethereum not enabled")
                    Pair(nativeEth as EVMToken, true)
                }
                CoinType.USDC -> {
                    val usdcToken = wallet.evmTokens.filterIsInstance<USDCToken>().find {
                        when (network.lowercase()) {
                            "mainnet" -> it.network == EthereumNetwork.Mainnet
                            "sepolia" -> it.network == EthereumNetwork.Sepolia
                            else -> true
                        }
                    } ?: wallet.evmTokens.filterIsInstance<USDCToken>().firstOrNull()
                    ?: return Result.Error("USDC not enabled")
                    Pair(usdcToken as EVMToken, false)
                }
                else -> return Result.Error("Invalid coin type")
            }

            // 3. Fetch fresh native transactions
            val nativeTxResult = evmBlockchainRepository.getNativeTransactions(
                address = token.address,
                network = token.network,
                walletId = walletId,
                tokenExternalId = token.externalId
            )

            if (nativeTxResult is Result.Success) {
                nativeTxResult.data.forEach { tx ->
                    evmTransactionRepository.saveTransaction(tx)
                }
                logger.d(tag, "Synced ${nativeTxResult.data.size} native transactions")
            }

            // 4. Fetch fresh token transactions (if not native ETH)
            if (!isEth) {
                val tokenTxResult = evmBlockchainRepository.getTokenTransactions(
                    address = token.address,
                    tokenContract = token.contractAddress,
                    network = token.network,
                    walletId = walletId,
                    tokenExternalId = token.externalId
                )

                if (tokenTxResult is Result.Success) {
                    tokenTxResult.data.forEach { tx ->
                        evmTransactionRepository.saveTransaction(tx)
                    }
                    logger.d(tag, "Synced ${tokenTxResult.data.size} token transactions")
                }
            }

            // 5. Get balance
            val balance = walletRepository.getWalletBalance(walletId)
            val balanceMap = balance?.evmBalances?.associateBy { it.externalTokenId } ?: emptyMap()
            val tokenBalance = balanceMap[token.externalId]

            // 6. Get ETH balance for gas (for USDC)
            var ethGasBalance: BigDecimal? = null
            if (!isEth) {
                val nativeEth = wallet.evmTokens.filterIsInstance<NativeETH>().find {
                    it.network == token.network
                }
                ethGasBalance = nativeEth?.let {
                    balanceMap[it.externalId]?.balanceDecimal?.toBigDecimalOrNull()
                }
            }

            // 7. Get transactions from local DB
            val allTxs = evmTransactionRepository.getTransactionsSync(walletId)
            val filteredTxs = when (coinType) {
                CoinType.ETHEREUM -> allTxs.filterIsInstance<NativeETHTransaction>()
                CoinType.USDC -> allTxs.filterIsInstance<TokenTransaction>()
                    .filter { tx -> tx.tokenSymbol == "USDC" && tx.tokenExternalId == token.externalId }
                else -> emptyList()
            }

            val displayTransactions = formatTransactionDisplayUseCase.formatTransactionList(
                filteredTxs,
                coinType
            )

            val result = EthereumDetailResult(
                walletId = walletId,
                address = token.address,
                balance = tokenBalance?.balanceDecimal ?: "0",
                balanceFormatted = when (token) {
                    is USDCToken, is USDTToken ->
                        "$${tokenBalance?.balanceDecimal?.toBigDecimalOrNull()?.setScale(2) ?: "0"} ${token.symbol}"
                    else ->
                        "${tokenBalance?.balanceDecimal ?: "0"} ${token.symbol}"
                },
                usdValue = tokenBalance?.usdValue ?: 0.0,
                network = token.network.displayName,
                networkDisplayName = token.network.displayName,
                transactions = displayTransactions,
                token = token,
                externalTokenId = token.externalId,
                ethGasBalance = ethGasBalance,
                availableTokens = wallet.evmTokens.filter { it.network == token.network }
            )

            logger.d(tag, "Successfully retrieved $coinType details")
            Result.Success(result)

        } catch (e: Exception) {
            logger.e(tag, "Error getting $coinType details", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }
}