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
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.bitcoin.toDomain
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken


interface GetSolanaDetailUseCase {
    suspend operator fun invoke(
        walletId: String,
        network: String = ""
    ): Result<SolanaDetailResult>
}

@Singleton
class GetSolanaDetailUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
    private val logger: Logger
) : GetSolanaDetailUseCase {

    private val tag = "GetSolanaDetailUC"

    override suspend operator fun invoke(
        walletId: String,
        network: String
    ): Result<SolanaDetailResult> {
        return try {
            logger.d(tag, "Getting Solana details for wallet: $walletId, network: $network")

            // 1. Get wallet
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found")

            // 2. Find the specific Solana coin
            val solanaCoin = wallet.solanaCoins.find {
                when (network.lowercase()) {
                    "mainnet" -> it.network == SolanaNetwork.Mainnet
                    "devnet" -> it.network == SolanaNetwork.Devnet
                    else -> true
                }
            } ?: wallet.solanaCoins.firstOrNull()
            ?: return Result.Error("Solana not enabled")

            val networkParam = when (solanaCoin.network) {
                SolanaNetwork.Mainnet -> "mainnet"
                SolanaNetwork.Devnet -> "devnet"
            }

            // 3. Fetch fresh transactions from blockchain
            val txResult = solanaBlockchainRepository.getFullTransactionHistory(
                address = solanaCoin.address,
                network = solanaCoin.network,
                limit = 50
            )

            if (txResult is Result.Success) {
                // Delete old transactions
                solanaTransactionRepository.deleteForWalletAndNetwork(walletId, networkParam)

                // Save new transactions
                txResult.data.forEach { (sigInfo, details) ->
                    if (details != null) {
                        val transferInfo = solanaBlockchainRepository.parseTransferFromDetails(
                            details = details,
                            walletAddress = solanaCoin.address
                        )

                        if (transferInfo != null) {
                            val transaction = SolanaTransaction(
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
                                feeLevel = FeeLevel.NORMAL,
                                amountLamports = transferInfo.amount,
                                amountSol = (transferInfo.amount.toDouble() / 1_000_000_000).toString(),
                                feeLamports = transferInfo.fee,
                                feeSol = (transferInfo.fee.toDouble() / 1_000_000_000).toString(),
                                signature = sigInfo.signature,
                                network = solanaCoin.network,
                                isIncoming = transferInfo.isIncoming,
                                tokenMint = null,
                                tokenSymbol = null,
                                tokenDecimals = null,
                                slot = sigInfo.slot,
                                blockTime = sigInfo.blockTime
                            )
                            solanaTransactionRepository.saveTransaction(transaction)
                        }
                    }
                }
                logger.d(tag, "Synced transactions")
            }

            // 4. Get balance
            val balance = walletRepository.getWalletBalance(walletId)
            val networkKey = when (solanaCoin.network) {
                SolanaNetwork.Mainnet -> "mainnet"
                SolanaNetwork.Devnet -> "devnet"
            }
            val coinBalance = balance?.solanaBalances?.get(networkKey)

            // 5. Get transactions from local DB
            val transactions = solanaTransactionRepository.getTransactionsSync(walletId, networkParam)
            val solTxs = transactions.filter { it.tokenSymbol == null }
            val displayTransactions = formatTransactionDisplayUseCase.formatTransactionList(
                solTxs,
                CoinType.SOLANA
            )

            val result = SolanaDetailResult(
                walletId = walletId,
                address = solanaCoin.address,
                balance = coinBalance?.sol ?: "0",
                balanceFormatted = "${coinBalance?.sol ?: "0"} SOL",
                usdValue = coinBalance?.usdValue ?: 0.0,
                network = solanaCoin.network.name,
                networkDisplayName = when (solanaCoin.network) {
                    SolanaNetwork.Mainnet -> "Mainnet"
                    SolanaNetwork.Devnet -> "Devnet"
                },
                transactions = displayTransactions,
                solanaCoin = solanaCoin,
                splTokens = solanaCoin.splTokens,
                availableNetworks = wallet.solanaCoins.map { it.network }
            )

            logger.d(tag, "Successfully retrieved Solana details")
            Result.Success(result)

        } catch (e: Exception) {
            logger.e(tag, "Error getting Solana details", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }
}