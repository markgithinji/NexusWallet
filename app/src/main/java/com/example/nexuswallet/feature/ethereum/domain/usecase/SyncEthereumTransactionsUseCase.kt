package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.ERC20Token
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEthereumTransactionsUseCase @Inject constructor(
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val walletRepository: WalletRepository,
    private val logger: Logger
) {

    private val tag = "SyncEthUC"

    suspend operator fun invoke(
        walletId: String,
        tokenExternalId: String? = null,
        network: EthereumNetwork? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        logger.d(
            tag,
            "=== Syncing EVM transactions for wallet: $walletId, token: $tokenExternalId, network: ${network?.displayName} ==="
        )

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Get all EVM tokens or filter by specific token and network
        val evmTokens = when {
            tokenExternalId != null -> wallet.evmTokens.filter { it.externalId == tokenExternalId }
            network != null -> wallet.evmTokens.filter { it.network == network }
            else -> wallet.evmTokens
        }

        if (evmTokens.isEmpty()) {
            val msg = when {
                tokenExternalId != null -> "No token found with ID: $tokenExternalId"
                network != null -> "No tokens found for network: ${network.displayName}"
                else -> "No EVM tokens found for wallet: $walletId"
            }
            logger.d(tag, msg)
            return@withContext Result.Success(Unit)
        }

        var totalTransactions = 0
        val errors = mutableListOf<String>()

        // Sync transactions for each token
        for (token in evmTokens) {
            logger.d(tag, "Syncing ${token.symbol} on ${token.network.displayName}")

            // Delete existing transactions for this specific token
            evmTransactionRepository.deleteForWalletAndToken(walletId, token.externalId)

            val result = when (token) {
                is NativeETH -> evmBlockchainRepository.getNativeTransactions(
                    address = token.address,
                    network = token.network,
                    walletId = walletId,
                    tokenExternalId = token.externalId
                )

                is USDCToken, is USDTToken, is ERC20Token -> evmBlockchainRepository.getTokenTransactions(
                    address = token.address,
                    tokenContract = token.contractAddress,
                    network = token.network,
                    walletId = walletId,
                    tokenExternalId = token.externalId
                )
            }

            when (result) {
                is Result.Success -> {
                    val transactions = result.data
                    transactions.forEach { transaction ->
                        evmTransactionRepository.saveTransaction(transaction)
                    }
                    totalTransactions += transactions.size
                    logger.d(
                        tag,
                        " Synced ${transactions.size} ${token.symbol} transactions on ${token.network.displayName}"
                    )
                }

                is Result.Error -> {
                    val errorMsg =
                        "Failed to sync ${token.symbol} on ${token.network.displayName}: ${result.message}"
                    logger.w(tag, errorMsg)
                    errors.add(errorMsg)
                }

                Result.Loading -> {}
            }
        }

        if (errors.isNotEmpty()) {
            logger.w(tag, "Sync completed with ${errors.size} errors")
            // Return success but log errors - don't fail the whole sync
        }

        logger.d(tag, " Successfully saved $totalTransactions total transactions")
        logger.d(tag, "=== Sync completed successfully for wallet $walletId ===")
        Result.Success(Unit)
    }
}