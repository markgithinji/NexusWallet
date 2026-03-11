package com.example.nexuswallet.feature.coin.ethereum.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SafeApiCall
import com.example.nexuswallet.feature.coin.SendValidationResult
import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.ERC20Token
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
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

    suspend operator fun invoke(walletId: String, tokenExternalId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        logger.d(tag, "=== Syncing EVM transactions for wallet: $walletId, token: $tokenExternalId ===")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Get all EVM tokens or filter by specific token
        val evmTokens = if (tokenExternalId != null) {
            wallet.evmTokens.filter { it.externalId == tokenExternalId }
        } else {
            wallet.evmTokens
        }

        if (evmTokens.isEmpty()) {
            logger.d(tag, "No EVM tokens found for wallet: $walletId")
            return@withContext Result.Success(Unit)
        }

        var totalTransactions = 0

        // Sync transactions for each token
        for (token in evmTokens) {
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
                    logger.d(tag, "Synced ${transactions.size} ${token.symbol} transactions on ${token.network.displayName}")
                }
                is Result.Error -> {
                    logger.w(tag, "Failed to sync ${token.symbol}: ${result.message}")
                }
                Result.Loading -> {}
            }
        }

        logger.d(tag, "Successfully saved $totalTransactions total transactions")
        logger.d(tag, "=== Sync completed successfully for wallet $walletId ===")
        Result.Success(Unit)
    }
}