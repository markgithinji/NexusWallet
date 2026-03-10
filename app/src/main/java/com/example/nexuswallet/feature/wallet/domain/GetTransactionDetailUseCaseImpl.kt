package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.EVMTransaction
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

interface GetTransactionDetailUseCase {
    suspend operator fun invoke(
        walletId: String,
        transactionId: String,
        coinType: CoinType
    ): Result<TransactionDetail>
}
@Singleton
class GetTransactionDetailUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val logger: Logger
) : GetTransactionDetailUseCase {

    private val tag = "GetTransactionDetailUC"

    override suspend fun invoke(
        walletId: String,
        transactionId: String,
        coinType: CoinType
    ): Result<TransactionDetail> {
        logger.d(tag, "Getting transaction detail: $transactionId for wallet: $walletId, type: $coinType")

        return try {
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found")

            val transaction = when (coinType) {
                CoinType.BITCOIN -> getBitcoinTransaction(transactionId)
                CoinType.ETHEREUM, CoinType.USDC -> getEVMTransaction(transactionId)
                CoinType.SOLANA -> getSolanaTransaction(transactionId)
            }

            if (transaction == null) {
                return Result.Error("Transaction not found")
            }

            val detail = when (transaction) {
                is BitcoinTransaction -> mapBitcoinToDetail(transaction)
                is SolanaTransaction -> mapSolanaToDetail(transaction)
                is NativeETHTransaction -> mapNativeETHToDetail(transaction, coinType)
                is TokenTransaction -> mapTokenToDetail(transaction, coinType)
                else -> return Result.Error("Unknown transaction type")
            }

            Result.Success(detail)

        } catch (e: Exception) {
            logger.e(tag, "Error getting transaction detail", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun getBitcoinTransaction(transactionId: String): BitcoinTransaction? {
        return bitcoinTransactionRepository.getTransaction(transactionId)
    }

    private suspend fun getEVMTransaction(transactionId: String): EVMTransaction? {
        return evmTransactionRepository.getTransaction(transactionId)
    }

    private suspend fun getSolanaTransaction(transactionId: String): SolanaTransaction? {
        return solanaTransactionRepository.getTransaction(transactionId)
    }

    // Extension function to get EthereumNetwork from chain ID
    private fun Long.toEthereumNetwork(): EthereumNetwork? {
        val chainIdStr = this.toString()
        return when (chainIdStr) {
            EthereumNetwork.Mainnet.chainId -> EthereumNetwork.Mainnet
            EthereumNetwork.Sepolia.chainId -> EthereumNetwork.Sepolia
            else -> {
                logger.w(tag, "Unknown chain ID: $this")
                null
            }
        }
    }

    // Extension function to get display name from chain ID
    private fun Long.toEthereumDisplayName(): String {
        val chainIdStr = this.toString()
        return when (chainIdStr) {
            EthereumNetwork.Mainnet.chainId -> EthereumNetwork.Mainnet.displayName
            EthereumNetwork.Sepolia.chainId -> EthereumNetwork.Sepolia.displayName
            else -> {
                logger.w(tag, "Unknown chain ID: $this, defaulting to Ethereum")
                "Ethereum"
            }
        }
    }

    private fun mapBitcoinToDetail(
        tx: BitcoinTransaction
    ): TransactionDetail {
        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coinType = CoinType.BITCOIN,
            network = when (tx.network) {
                BitcoinNetwork.Mainnet -> "Bitcoin Mainnet"
                BitcoinNetwork.Testnet -> "Bitcoin Testnet"
            },
            hash = tx.txHash ?: tx.id,
            status = tx.status,
            timestamp = tx.timestamp,
            fromAddress = tx.fromAddress,
            toAddress = tx.toAddress,
            amount = tx.amountBtc,
            fee = tx.feeBtc,
            isIncoming = tx.isIncoming,
            memo = tx.note,
            blockHeight = null,
            confirmations = null,
            feePerByte = tx.feePerByte,
            estimatedSize = tx.estimatedSize.toInt()
        )
    }

    private fun mapSolanaToDetail(
        tx: SolanaTransaction
    ): TransactionDetail {
        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coinType = CoinType.SOLANA,
            network = when (tx.network) {
                SolanaNetwork.Mainnet -> "Solana Mainnet"
                SolanaNetwork.Devnet -> "Solana Devnet"
            },
            hash = tx.signature ?: tx.id,
            status = tx.status,
            timestamp = tx.timestamp,
            fromAddress = tx.fromAddress,
            toAddress = tx.toAddress,
            amount = tx.amountSol,
            fee = tx.feeSol,
            isIncoming = tx.isIncoming,
            memo = tx.note,
            blockHeight = tx.blockTime,
            slot = tx.slot,
            tokenSymbol = tx.tokenSymbol,
            tokenDecimals = tx.tokenDecimals
        )
    }

    private fun mapNativeETHToDetail(
        tx: NativeETHTransaction,
        coinType: CoinType
    ): TransactionDetail {
        val gasPriceGwei = tx.gasPriceGwei.toDoubleOrNull() ?: 0.0
        val gasUsed = tx.gasLimit
        val feeEth = (gasPriceGwei * gasUsed / 1_000_000_000).toString()

        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coinType = coinType,
            network = tx.chainId.toEthereumDisplayName(), // Now works with Long
            hash = tx.txHash ?: tx.id,
            status = tx.status,
            timestamp = tx.timestamp,
            fromAddress = tx.fromAddress,
            toAddress = tx.toAddress,
            amount = tx.amountEth,
            fee = feeEth,
            isIncoming = tx.isIncoming,
            memo = tx.note,
            gasPrice = tx.gasPriceGwei,
            gasUsed = gasUsed,
            nonce = tx.nonce,
            chainId = tx.chainId.toString()
        )
    }

    private fun mapTokenToDetail(
        tx: TokenTransaction,
        coinType: CoinType
    ): TransactionDetail {
        val gasPriceGwei = tx.gasPriceGwei.toDoubleOrNull() ?: 0.0
        val gasUsed = tx.gasLimit
        val feeEth = (gasPriceGwei * gasUsed / 1_000_000_000).toString()

        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coinType = coinType,
            network = tx.chainId.toEthereumDisplayName(),
            hash = tx.txHash ?: tx.id,
            status = tx.status,
            timestamp = tx.timestamp,
            fromAddress = tx.fromAddress,
            toAddress = tx.toAddress,
            amount = tx.amountDecimal,
            fee = feeEth,
            isIncoming = tx.isIncoming,
            memo = tx.note,
            gasPrice = tx.gasPriceGwei,
            gasUsed = gasUsed,
            nonce = tx.nonce,
            chainId = tx.chainId.toString(),
            tokenSymbol = tx.tokenSymbol,
            tokenDecimals = tx.tokenDecimals,
            tokenContract = tx.tokenContract
        )
    }
}