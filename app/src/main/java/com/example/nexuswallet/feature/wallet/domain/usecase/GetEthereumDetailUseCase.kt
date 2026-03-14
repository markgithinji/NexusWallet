package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.ethereum.domain.model.TokenTransaction
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumDetailResult
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetEthereumDetailUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val logger: Logger
) {

    private val tag = "GetEthereumDetailUC"

    suspend fun getEthDetails(
        walletId: String,
        network: EthereumNetwork
    ): Result<EthereumDetailResult> = getDetails(walletId, network, CoinType.ETHEREUM)

    suspend fun getUsdcDetails(
        walletId: String,
        network: EthereumNetwork
    ): Result<EthereumDetailResult> = getDetails(walletId, network, CoinType.USDC)

    private suspend fun getDetails(
        walletId: String,
        network: EthereumNetwork,
        coinType: CoinType
    ): Result<EthereumDetailResult> {
        logger.d(tag, "Getting $coinType details for wallet: $walletId, network: ${network.displayName}")

        // 1. Get wallet
        val wallet = walletRepository.getWallet(walletId)
            ?: return Result.Error("Wallet not found")

        // 2. Find the specific token with network awareness
        val (token, isEth) = when (coinType) {
            CoinType.ETHEREUM -> {
                val nativeEth = wallet.evmTokens.filterIsInstance<NativeETH>().find { it.network == network }
                    ?: wallet.evmTokens.filterIsInstance<NativeETH>().firstOrNull()
                    ?: return Result.Error("Ethereum not enabled for ${network.displayName}")
                Pair(nativeEth as EVMToken, true)
            }
            CoinType.USDC -> {
                val usdcToken = wallet.evmTokens.filterIsInstance<USDCToken>().find { it.network == network }
                    ?: wallet.evmTokens.filterIsInstance<USDCToken>().firstOrNull()
                    ?: return Result.Error("USDC not enabled for ${network.displayName}")
                Pair(usdcToken as EVMToken, false)
            }
            else -> return Result.Error("Invalid coin type")
        }

        logger.d(tag, "Found token: ${token.symbol} with address: ${token.address.take(8)}... on ${network.displayName}")

        // 3. Fetch fresh native transactions
        logger.d(tag, "Fetching native transactions from blockchain for ${network.displayName}...")
        val nativeTxResult = evmBlockchainRepository.getNativeTransactions(
            address = token.address,
            network = network,
            walletId = walletId,
            tokenExternalId = token.externalId
        )

        when (nativeTxResult) {
            is Result.Success -> {
                nativeTxResult.data.forEach { tx ->
                    evmTransactionRepository.saveTransaction(tx)
                }
                logger.d(tag, " Synced ${nativeTxResult.data.size} native transactions for ${network.displayName}")
            }
            is Result.Error -> {
                logger.e(tag, " Failed to fetch native transactions: ${nativeTxResult.message}")
                // Continue with existing transactions
            }
            Result.Loading -> {}
        }

        // 4. Fetch fresh token transactions (if not native ETH)
        if (!isEth) {
            logger.d(tag, "Fetching token transactions from blockchain for ${network.displayName}...")
            val tokenTxResult = evmBlockchainRepository.getTokenTransactions(
                address = token.address,
                tokenContract = token.contractAddress,
                network = network,
                walletId = walletId,
                tokenExternalId = token.externalId
            )

            when (tokenTxResult) {
                is Result.Success -> {
                    tokenTxResult.data.forEach { tx ->
                        evmTransactionRepository.saveTransaction(tx)
                    }
                    logger.d(tag, " Synced ${tokenTxResult.data.size} token transactions for ${network.displayName}")
                }
                is Result.Error -> {
                    logger.e(tag, " Failed to fetch token transactions: ${tokenTxResult.message}")
                }
                Result.Loading -> {}
            }
        }

        // 5. Get balance
        val balance = walletRepository.getWalletBalance(walletId)
        val balanceMap = balance?.evmBalances?.associateBy { it.externalTokenId } ?: emptyMap()
        val tokenBalance = balanceMap[token.externalId]

        // 6. Get ETH balance for gas (for USDC)
        var ethGasBalance: BigDecimal? = null
        if (!isEth) {
            val nativeEth = wallet.evmTokens.filterIsInstance<NativeETH>().find { it.network == network }
            ethGasBalance = nativeEth?.let {
                balanceMap[it.externalId]?.balanceDecimal?.toBigDecimalOrNull()
            }
            logger.d(tag, "ETH gas balance for ${network.displayName}: $ethGasBalance")
        }

        // 7. Get raw transactions from local DB
        logger.d(tag, "Querying transactions from local DB for ${network.displayName}...")
        val allTxs = evmTransactionRepository.getTransactionsSync(walletId)
        val filteredTxs = when (coinType) {
            CoinType.ETHEREUM -> {
                allTxs.filterIsInstance<NativeETHTransaction>()
                    .filter { it.network == network }
            }
            CoinType.USDC -> {
                allTxs.filterIsInstance<TokenTransaction>()
                    .filter { tx ->
                        tx.tokenExternalId == token.externalId &&
                                tx.network == network
                    }
            }
            else -> emptyList()
        }

        logger.d(tag, "Retrieved ${filteredTxs.size} filtered transactions from DB")

        // Format balance based on token type
        val balanceFormatted = when {
            token is USDCToken || token is USDTToken -> {
                val numericBalance = tokenBalance?.balanceDecimal?.toBigDecimalOrNull()
                if (numericBalance != null) {
                    "$${numericBalance.setScale(2)} ${token.symbol}"
                } else {
                    "0 ${token.symbol}"
                }
            }
            else -> {
                "${tokenBalance?.balanceDecimal ?: "0"} ${token.symbol}"
            }
        }

        val result = EthereumDetailResult(
            walletId = walletId,
            address = token.address,
            balance = tokenBalance?.balanceDecimal ?: "0",
            balanceFormatted = balanceFormatted,
            usdValue = tokenBalance?.usdValue ?: 0.0,
            network = network,
            networkDisplayName = network.displayName,
            rawTransactions = filteredTxs,
            token = token,
            externalTokenId = token.externalId,
            ethGasBalance = ethGasBalance,
            availableTokens = wallet.evmTokens.filter { it.network == network },
            chainId = network.chainId
        )

        logger.d(tag, "=== GetEthereumDetailUseCase completed successfully with ${filteredTxs.size} raw transactions on ${network.displayName} ===")
        return Result.Success(result)
    }
}