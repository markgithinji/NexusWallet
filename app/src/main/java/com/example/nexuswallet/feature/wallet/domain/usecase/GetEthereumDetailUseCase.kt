package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumDetailResult
import com.example.nexuswallet.feature.ethereum.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository

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

            // 2. Find the specific token with network awareness
            val (token, isEth, targetNetwork) = when (coinType) {
                CoinType.ETHEREUM -> {
                    val nativeEth = wallet.evmTokens.filterIsInstance<NativeETH>().find {
                        when (network.lowercase()) {
                            "mainnet" -> it.network == EthereumNetwork.Mainnet
                            "sepolia" -> it.network == EthereumNetwork.Sepolia
                            else -> true
                        }
                    } ?: wallet.evmTokens.filterIsInstance<NativeETH>().firstOrNull()
                    ?: return Result.Error("Ethereum not enabled")
                    Triple(nativeEth as EVMToken, true, nativeEth.network)
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
                    Triple(usdcToken as EVMToken, false, usdcToken.network)
                }
                else -> return Result.Error("Invalid coin type")
            }

            // 3. Fetch fresh native transactions
            val nativeTxResult = evmBlockchainRepository.getNativeTransactions(
                address = token.address,
                network = targetNetwork,
                walletId = walletId,
                tokenExternalId = token.externalId
            )

            if (nativeTxResult is Result.Success) {
                nativeTxResult.data.forEach { tx ->
                    evmTransactionRepository.saveTransaction(tx)
                }
                logger.d(tag, "Synced ${nativeTxResult.data.size} native transactions for ${targetNetwork.displayName}")
            }

            // 4. Fetch fresh token transactions (if not native ETH)
            if (!isEth) {
                val tokenTxResult = evmBlockchainRepository.getTokenTransactions(
                    address = token.address,
                    tokenContract = token.contractAddress,
                    network = targetNetwork,
                    walletId = walletId,
                    tokenExternalId = token.externalId
                )

                if (tokenTxResult is Result.Success) {
                    tokenTxResult.data.forEach { tx ->
                        evmTransactionRepository.saveTransaction(tx)
                    }
                    logger.d(tag, "Synced ${tokenTxResult.data.size} token transactions for ${targetNetwork.displayName}")
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
                    it.network == targetNetwork
                }
                ethGasBalance = nativeEth?.let {
                    balanceMap[it.externalId]?.balanceDecimal?.toBigDecimalOrNull()
                }
            }

            // 7. Get raw transactions from local DB
            val allTxs = evmTransactionRepository.getTransactionsSync(walletId)
            val filteredTxs = when (coinType) {
                CoinType.ETHEREUM -> {
                    allTxs.filterIsInstance<NativeETHTransaction>()
                        .filter { tx -> tx.network == targetNetwork.displayName }
                }
                CoinType.USDC -> {
                    allTxs.filterIsInstance<TokenTransaction>()
                        .filter { tx ->
                            tx.tokenSymbol == "USDC" &&
                                    tx.tokenExternalId == token.externalId &&
                                    tx.network == targetNetwork.displayName
                        }
                }
                else -> emptyList()
            }

            val result = EthereumDetailResult(
                walletId = walletId,
                address = token.address,
                balance = tokenBalance?.balanceDecimal ?: "0",
                balanceFormatted = when (token) {
                    is USDCToken, is USDTToken ->
                        "$${
                            tokenBalance?.balanceDecimal?.toBigDecimalOrNull()?.setScale(2) ?: "0"
                        } ${token.symbol}"

                    else ->
                        "${tokenBalance?.balanceDecimal ?: "0"} ${token.symbol}"
                },
                usdValue = tokenBalance?.usdValue ?: 0.0,
                network = targetNetwork.displayName,
                networkDisplayName = targetNetwork.displayName,
                rawTransactions = filteredTxs,
                token = token,
                externalTokenId = token.externalId,
                ethGasBalance = ethGasBalance,
                availableTokens = wallet.evmTokens.filter { it.network == targetNetwork },
                chainId = targetNetwork.chainId
            )

            logger.d(tag, "Successfully retrieved $coinType details for ${targetNetwork.displayName} with ${filteredTxs.size} raw transactions")
            Result.Success(result)

        } catch (e: Exception) {
            logger.e(tag, "Error getting $coinType details", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }
}