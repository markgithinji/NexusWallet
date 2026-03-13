package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTransaction
import com.example.nexuswallet.feature.ethereum.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.ethereum.domain.model.TokenTransaction
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDetail
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetTransactionDetailUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val logger: Logger
) {

    private val tag = "GetTransactionDetailUC"

    suspend operator fun invoke(
        walletId: String,
        transactionId: String
    ): Result<TransactionDetail> {
        logger.d(
            tag,
            "Getting transaction detail: $transactionId for wallet: $walletId"
        )

        return try {
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found")

            // Try each repository until we find the transaction
            val transaction = getBitcoinTransaction(transactionId)
                ?: getEVMTransaction(transactionId)
                ?: getSolanaTransaction(transactionId)

            if (transaction == null) {
                return Result.Error("Transaction not found")
            }

            val detail = when (transaction) {
                is BitcoinTransaction -> mapBitcoinToDetail(transaction)
                is SolanaTransaction -> mapSolanaToDetail(transaction)
                is NativeETHTransaction -> mapNativeETHToDetail(transaction)
                is TokenTransaction -> mapTokenToDetail(transaction)
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

    private fun mapBitcoinToDetail(
        tx: BitcoinTransaction
    ): TransactionDetail {
        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coinType = CoinType.BITCOIN,
            network = tx.network,
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
            network = tx.network,
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
        tx: NativeETHTransaction
    ): TransactionDetail {
        val gasPriceGwei = tx.gasPriceGwei.toDoubleOrNull() ?: 0.0
        val gasUsed = tx.gasLimit
        val feeEth = (gasPriceGwei * gasUsed / 1_000_000_000).toString()

        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coinType = CoinType.ETHEREUM,
            network = tx.network,
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
        tx: TokenTransaction
    ): TransactionDetail {
        val gasPriceGwei = tx.gasPriceGwei.toDoubleOrNull() ?: 0.0
        val gasUsed = tx.gasLimit
        val feeEth = (gasPriceGwei * gasUsed / 1_000_000_000).toString()

        // Determine coin type based on token symbol
        val coinType = when (tx.tokenSymbol) {
            "USDC" -> CoinType.USDC
            "USDT" -> CoinType.USDC // Treat USDT as USDC for navigation purposes
            else -> CoinType.ETHEREUM
        }

        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coinType = coinType,
            network = tx.network,
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
