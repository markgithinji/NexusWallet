package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumDetailResult
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetEthereumDetailUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val syncEVMBalancesUseCase: SyncEVMBalancesUseCase,
    private val getSimplePricesUseCase: GetSimplePricesUseCase,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        walletId: String,
        token: EVMToken
    ): Result<EthereumDetailResult> = withContext(ioDispatcher) {
        val network = token.network

        logger.d(TAG, "Getting ${token.symbol} details for wallet: $walletId, network: ${network.name}")

        // 1. Get wallet
        val wallet = walletRepository.getWallet(walletId)
            ?: return@withContext Result.Error("Wallet not found")

        // 2. Verify the token belongs to this wallet
        val verifiedToken = wallet.evmTokens.find {
            it.address == token.address &&
                    it.network == network &&
                    it.evmTokenType == token.evmTokenType
        } ?: return@withContext Result.Error("${token.symbol} not enabled for ${network.name}")

        val isEth = verifiedToken is NativeETH

        logger.d(TAG, "Found token: ${verifiedToken.symbol} with address: ${verifiedToken.address.take(8)}... on ${network.name}")

        // 3. Fetch fresh native transactions
        logger.d(TAG, "Fetching native transactions from blockchain for ${network.name}...")
        val nativeTxResult = evmBlockchainRepository.getNativeTransactions(
            address = verifiedToken.address,
            network = network,
            walletId = walletId,
            evmTokenType = verifiedToken.evmTokenType
        )

        when (nativeTxResult) {
            is Result.Success -> {
                nativeTxResult.data.forEach { tx ->
                    evmTransactionRepository.saveTransaction(tx)
                }
                logger.d(TAG, "Synced ${nativeTxResult.data.size} native transactions for ${network.name}")
            }
            is Result.Error -> {
                logger.e(TAG, "Failed to fetch native transactions: ${nativeTxResult.message}")
            }
            Result.Loading -> {}
        }

        // 4. Fetch fresh token transactions (if not native ETH)
        if (!isEth) {
            logger.d(TAG, "Fetching token transactions from blockchain for ${network.name}...")
            val tokenTxResult = evmBlockchainRepository.getTokenTransactions(
                address = verifiedToken.address,
                tokenContract = verifiedToken.contractAddress,
                network = network,
                walletId = walletId,
                evmTokenType = verifiedToken.evmTokenType
            )

            when (tokenTxResult) {
                is Result.Success -> {
                    tokenTxResult.data.forEach { tx ->
                        evmTransactionRepository.saveTransaction(tx)
                    }
                    logger.d(TAG, "Synced ${tokenTxResult.data.size} token transactions for ${network.name}")
                }
                is Result.Error -> {
                    logger.e(TAG, "Failed to fetch token transactions: ${tokenTxResult.message}")
                }
                Result.Loading -> {}
            }
        }

        // 4.5. Sync fresh balances
        try {
            val currency = settingsRepository.getSelectedCurrency()
            val pricesResult = getSimplePricesUseCase(wallet.evmTokens.map { it.symbol }, currency)
            val prices = if (pricesResult is Result.Success) pricesResult.data else emptyMap()
            
            syncEVMBalancesUseCase(walletId, wallet.evmTokens, prices)
            logger.d(TAG, "Synced balances for wallet: $walletId")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to sync balances", e)
        }

        // 5. Get balance (Already synced)
        val balance = walletRepository.getWalletBalance(walletId)
        val lookupKey = "${verifiedToken.network.chainId}_${verifiedToken.contractAddress}"
        val tokenBalance = balance?.evmBalances?.get(lookupKey)

        // 6. Get ETH balance for gas (for tokens)
        var ethGasBalance: String? = null
        if (!isEth) {
            val nativeEth = wallet.evmTokens.filterIsInstance<NativeETH>().find { it.network == network }
            ethGasBalance = nativeEth?.let { nativeToken ->
                val nativeLookupKey = "${nativeToken.network.chainId}_${nativeToken.contractAddress}"
                balance?.evmBalances?.get(nativeLookupKey)?.balanceDecimal?.toBigDecimalOrNull()
            }?.stripTrailingZeros()?.toPlainString()

            logger.d(TAG, "ETH gas balance for ${network.name}: $ethGasBalance")
        }

        // 7. Get raw transactions from local DB
        logger.d(TAG, "Querying transactions from local DB for ${network.name}...")
        val allTxs = evmTransactionRepository.getTransactionsSync(walletId)
        val filteredTxs = when {
            isEth -> {
                allTxs.filterIsInstance<NativeETHTransaction>()
                    .filter { it.network == network }
            }
            else -> {
                allTxs.filterIsInstance<TokenTransaction>()
                    .filter { tx ->
                        tx.evmTokenType == verifiedToken.evmTokenType &&
                                tx.network == verifiedToken.network
                    }
            }
        }

        logger.d(TAG, "Retrieved ${filteredTxs.size} filtered transactions from DB")

        // Format balance based on token type
        val balanceFormatted = when {
            verifiedToken is USDCToken || verifiedToken is USDTToken -> {
                val numericBalance = tokenBalance?.balanceDecimal?.toBigDecimalOrNull()
                if (numericBalance != null) {
                    // Show full precision (up to 6 decimals for USDT/USDC) to avoid rounding small balances to zero
                    // and removed the redundant '$' sign as this is the token amount, not fiat value.
                    "${numericBalance.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} ${verifiedToken.symbol}"
                } else {
                    "0 ${verifiedToken.symbol}"
                }
            }
            else -> { // NativeETH
                val numericBalance = tokenBalance?.balanceDecimal?.toBigDecimalOrNull()
                if (numericBalance != null) {
                    "${numericBalance.stripTrailingZeros().toPlainString()} ${verifiedToken.symbol}"
                } else {
                    "0 ${verifiedToken.symbol}"
                }
            }
        }

        val result = EthereumDetailResult(
            walletId = walletId,
            address = verifiedToken.address,
            balance = tokenBalance?.balanceDecimal ?: "0",
            balanceFormatted = balanceFormatted,
            usdValue = tokenBalance?.usdValue ?: BigDecimal.ZERO,
            network = network,
            networkDisplayName = network.name,
            rawTransactions = filteredTxs,
            token = verifiedToken,
            ethGasBalance = ethGasBalance ?: "0",
            availableTokens = wallet.evmTokens.filter { it.network == network },
            chainId = network.chainId
        )

        logger.d(TAG, "=== GetEthereumDetailUseCase completed successfully with ${filteredTxs.size} raw transactions on ${network.name} ===")
        Result.Success(result)
    }

    companion object {
        private const val TAG = "GetEthereumDetailUC"
    }
}