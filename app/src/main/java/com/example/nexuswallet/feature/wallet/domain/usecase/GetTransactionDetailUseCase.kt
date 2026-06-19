package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.EVMTransaction
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDetail
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetTransactionDetailUseCase @Inject constructor(
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val tag = "GetTransactionDetailUC"

    suspend operator fun invoke(
        walletId: String,
        transactionId: String,
        coin: Coin
    ): Result<TransactionDetail> = withContext(ioDispatcher) {
        logger.d(
            tag,
            "Getting ${coin.symbol} transaction detail: $transactionId for wallet: $walletId"
        )

        try {

            // Use the coin to determine which repository to query
            val transaction = when (coin) {
                is BitcoinCoin -> getBitcoinTransaction(transactionId)
                is SolanaCoin -> getSolanaTransaction(transactionId)
                is EVMToken -> getEVMTransaction(transactionId)
            }

            if (transaction == null) {
                return@withContext Result.Error("Transaction not found for ${coin.symbol}")
            }

            // Verify the transaction matches the expected coin/network
            val isValidTransaction = when (transaction) {
                is BitcoinTransaction -> coin is BitcoinCoin && transaction.network == coin.network
                is SolanaTransaction -> coin is SolanaCoin && transaction.network == coin.network
                is NativeETHTransaction -> coin is NativeETH && transaction.network == coin.network
                is TokenTransaction -> {
                    coin is EVMToken &&
                            transaction.evmTokenType == coin.evmTokenType &&
                            transaction.network == coin.network
                }
            }

            if (!isValidTransaction) {
                logger.e(tag, "Transaction type mismatch for coin ${coin.symbol}")
                return@withContext Result.Error("Transaction does not match the selected coin")
            }

            val detail = when (transaction) {
                is BitcoinTransaction -> mapBitcoinToDetail(transaction, coin)
                is SolanaTransaction -> mapSolanaToDetail(transaction, coin)
                is NativeETHTransaction -> mapNativeETHToDetail(transaction, coin)
                is TokenTransaction -> mapTokenToDetail(transaction, coin)
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
        tx: BitcoinTransaction,
        coin: Coin
    ): TransactionDetail {
        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coin = coin,
            network = tx.network,
            hash = tx.txHash ?: tx.id,
            status = tx.status,
            timestamp = tx.timestamp,
            fromAddress = tx.fromAddress,
            toAddress = tx.toAddress,
            amount = tx.amount,
            fee = tx.fee,
            isIncoming = tx.isIncoming,
            memo = tx.note,
            confirmations = null,
            feePerByte = tx.feePerByte,
            estimatedSize = tx.estimatedSize.toInt()
        )
    }

    private fun mapSolanaToDetail(
        tx: SolanaTransaction,
        coin: Coin
    ): TransactionDetail {
        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coin = coin,
            network = tx.network,
            hash = tx.signature ?: tx.id,
            status = tx.status,
            timestamp = tx.timestamp,
            fromAddress = tx.fromAddress,
            toAddress = tx.toAddress,
            amount = tx.amount,
            fee = tx.fee,
            isIncoming = tx.isIncoming,
            memo = tx.note,
            slot = tx.slot,
            tokenSymbol = tx.tokenSymbol,
            tokenDecimals = tx.tokenDecimals
        )
    }

    private fun mapNativeETHToDetail(
        tx: NativeETHTransaction,
        coin: Coin
    ): TransactionDetail {
        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coin = coin,
            network = tx.network,
            hash = tx.txHash ?: tx.id,
            status = tx.status,
            timestamp = tx.timestamp,
            fromAddress = tx.fromAddress,
            toAddress = tx.toAddress,
            amount = tx.amount,
            fee = tx.fee,
            isIncoming = tx.isIncoming,
            memo = tx.note,
            gasPrice = tx.gasPriceGwei,
            gasUsed = tx.gasLimit,
            nonce = tx.nonce,
            chainId = tx.chainId.toString()
        )
    }

    private fun mapTokenToDetail(
        tx: TokenTransaction,
        coin: Coin
    ): TransactionDetail {
        return TransactionDetail(
            id = tx.id,
            walletId = tx.walletId,
            coin = coin,
            network = tx.network,
            hash = tx.txHash ?: tx.id,
            status = tx.status,
            timestamp = tx.timestamp,
            fromAddress = tx.fromAddress,
            toAddress = tx.toAddress,
            amount = tx.amount,
            fee = tx.fee,
            isIncoming = tx.isIncoming,
            memo = tx.note,
            gasPrice = tx.gasPriceGwei,
            gasUsed = tx.gasLimit,
            nonce = tx.nonce,
            chainId = tx.chainId.toString(),
            tokenSymbol = coin.symbol,
            tokenDecimals = (coin as? EVMToken)?.decimals ?: 18,
            tokenContract = tx.tokenContract
        )
    }
}