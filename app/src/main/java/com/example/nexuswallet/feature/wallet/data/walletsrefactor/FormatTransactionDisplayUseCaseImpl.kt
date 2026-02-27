package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransaction
import com.example.nexuswallet.feature.wallet.domain.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import jakarta.inject.Inject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Singleton

@Singleton
class FormatTransactionDisplayUseCaseImpl @Inject constructor() : FormatTransactionDisplayUseCase {

    override operator fun invoke(
        transaction: Any,
        coinType: CoinType
    ): TransactionDisplayInfo {
        return when (transaction) {
            is BitcoinTransaction -> formatBitcoinTransaction(transaction, coinType)
            is EthereumTransaction -> formatEthereumTransaction(transaction, coinType)
            is SolanaTransaction -> formatSolanaTransaction(transaction, coinType)
            is USDCTransaction -> formatUSDCTransaction(transaction, coinType)
            else -> throw IllegalArgumentException("Unknown transaction type")
        }
    }

    override fun formatTransactionList(
        transactions: List<Any>,
        coinType: CoinType
    ): List<TransactionDisplayInfo> {
        return transactions.map { invoke(it, coinType) }
    }

    private fun formatBitcoinTransaction(
        tx: BitcoinTransaction,
        coinType: CoinType
    ): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = tx.id,
            isIncoming = tx.isIncoming,
            amount = tx.amountBtc,
            formattedAmount = formatAmount(tx.amountBtc),
            status = tx.status,
            timestamp = tx.timestamp,
            formattedTime = formatTimestamp(tx.timestamp),
            hash = tx.txHash
        )
    }

    private fun formatEthereumTransaction(
        tx: EthereumTransaction,
        coinType: CoinType
    ): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = tx.id,
            isIncoming = tx.isIncoming,
            amount = tx.amountEth,
            formattedAmount = formatAmount(tx.amountEth),
            status = tx.status,
            timestamp = tx.timestamp,
            formattedTime = formatTimestamp(tx.timestamp),
            hash = tx.txHash
        )
    }

    private fun formatSolanaTransaction(
        tx: SolanaTransaction,
        coinType: CoinType
    ): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = tx.id,
            isIncoming = tx.isIncoming,
            amount = tx.amountSol,
            formattedAmount = formatAmount(tx.amountSol),
            status = tx.status,
            timestamp = tx.timestamp,
            formattedTime = formatTimestamp(tx.timestamp),
            hash = tx.signature
        )
    }

    private fun formatUSDCTransaction(
        tx: USDCTransaction,
        coinType: CoinType
    ): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = tx.id,
            isIncoming = tx.isIncoming,
            amount = tx.amountDecimal,
            formattedAmount = formatAmount(tx.amountDecimal),
            status = tx.status,
            timestamp = tx.timestamp,
            formattedTime = formatTimestamp(tx.timestamp),
            hash = tx.txHash
        )
    }

    private fun formatAmount(amount: String): String {
        return try {
            val amountDecimal = amount.toBigDecimal()
            when {
                amountDecimal < BigDecimal("0.000001") ->
                    amountDecimal.setScale(8, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()

                amountDecimal < BigDecimal("0.001") ->
                    amountDecimal.setScale(6, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()

                amountDecimal < BigDecimal("1") ->
                    amountDecimal.setScale(4, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()

                else ->
                    amountDecimal.setScale(2, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()
            }
        } catch (e: Exception) {
            amount
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} hr ago"
            else -> {
                val date = Date(timestamp)
                SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }
        }
    }
}

data class TransactionDisplayInfo(
    val id: String,
    val isIncoming: Boolean,
    val amount: String,
    val formattedAmount: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val formattedTime: String,
    val hash: String?
)