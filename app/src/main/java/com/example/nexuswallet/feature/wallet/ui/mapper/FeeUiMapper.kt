package com.example.nexuswallet.feature.wallet.ui.mapper

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.ethereum.domain.model.EVMFeeEstimate
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.ui.theme.success
import com.example.nexuswallet.ui.theme.warning

object FeeUiMapper {

    @Composable
    fun mapToUiModel(estimate: Any): FeeUiModel {
        val priority = when (estimate) {
            is BitcoinFeeEstimate -> estimate.priority
            is EVMFeeEstimate -> estimate.priority
            is SolanaFeeEstimate -> estimate.priority
            else -> FeeLevel.NORMAL
        }

        return FeeUiModel(
            priority = priority,
            priorityLabel = priority.name.lowercase().replaceFirstChar { it.uppercase() },
            priorityColor = getPriorityColor(priority),
            feeDetails = mapFeeDetails(estimate),
            estimatedTimeText = mapEstimatedTime(estimate)
        )
    }

    @Composable
    private fun mapFeeDetails(estimate: Any): List<Pair<String, String>> {
        return when (estimate) {
            is BitcoinFeeEstimate -> listOf(
                stringResource(R.string.total_fee) to "${estimate.totalFeeBtc} BTC",
                stringResource(R.string.fee_rate) to "${estimate.feePerByte} sat/byte"
            )
            is EVMFeeEstimate -> listOf(
                stringResource(R.string.total_fee) to "${estimate.totalFeeEth} ETH",
                stringResource(R.string.gas_price) to "${estimate.gasPriceGwei} Gwei",
                stringResource(R.string.gas_limit) to estimate.gasLimit.toString()
            )
            is SolanaFeeEstimate -> listOf(
                stringResource(R.string.total_fee) to "${estimate.feeSol} SOL",
                stringResource(R.string.compute_units) to estimate.computeUnits.toString()
            )
            else -> emptyList()
        }
    }

    @Composable
    private fun mapEstimatedTime(estimate: Any): String? {
        val time = when (estimate) {
            is BitcoinFeeEstimate -> estimate.estimatedTime
            is EVMFeeEstimate -> estimate.estimatedTime
            is SolanaFeeEstimate -> estimate.estimatedTime
            else -> null
        }
        return time?.let { "~${it}s" }
    }

    @Composable
    private fun getPriorityColor(priority: FeeLevel): Color {
        return when (priority) {
            FeeLevel.SLOW -> MaterialTheme.colorScheme.success
            FeeLevel.NORMAL -> MaterialTheme.colorScheme.primary
            FeeLevel.FAST -> MaterialTheme.colorScheme.warning
        }
    }
}
