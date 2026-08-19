package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalculateBitcoinMaxAmountUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger
) {
    data class MaxAmountResult(
        val amountBtc: BigDecimal,
        val feeBtc: BigDecimal,
        val feeSatoshis: Long,
        val estimatedSize: Long,
        val inputCount: Int
    )

    suspend operator fun invoke(
        walletAddress: String,
        network: BitcoinNetwork,
        feeLevel: FeeLevel,
        balance: BigDecimal
    ): Result<MaxAmountResult> {
        val utxosResult = bitcoinBlockchainRepository.getUnspentOutputs(walletAddress, network)

        if (utxosResult !is Result.Success) {
            return Result.Error((utxosResult as Result.Error).message)
        }

        val allUtxos = utxosResult.data
        if (allUtxos.isEmpty()) {
            return Result.Error("No UTXOs available")
        }

        // Detect SegWit and calculate mixed input size
        val totalInputSize = allUtxos.fold(0L) { acc, item ->
            acc + when {
                org.bitcoinj.script.ScriptPattern.isP2WPKH(item.script) -> BitcoinConstants.BYTES_PER_INPUT_SEGWIT
                org.bitcoinj.script.ScriptPattern.isP2SH(item.script) -> BitcoinConstants.BYTES_PER_INPUT_P2SH
                else -> BitcoinConstants.BYTES_PER_INPUT
            }
        }
        val hasSegwit = allUtxos.any { org.bitcoinj.script.ScriptPattern.isP2WPKH(it.script) }

        // Sweep calculation has only 1 output (the recipient)
        val sweepOutputCount = 1
        val baseSize = if (hasSegwit) 11L else BitcoinConstants.BASE_TX_SIZE
        val outputSize =
            if (hasSegwit) (sweepOutputCount * BitcoinConstants.BYTES_PER_OUTPUT_SEGWIT) else (sweepOutputCount * BitcoinConstants.BYTES_PER_OUTPUT)
        val totalSize = baseSize + totalInputSize + outputSize

        // Get current fee rate
        val feePerByteResult = bitcoinBlockchainRepository.getFeeEstimate(
            feeLevel = feeLevel,
            inputCount = allUtxos.size,
            outputCount = sweepOutputCount,
            network = network,
            isSegwit = hasSegwit
        )

        if (feePerByteResult !is Result.Success) {
            return Result.Error("Failed to fetch fee rates")
        }

        val feePerByte = feePerByteResult.data.feePerByte
        val totalFeeSatoshis = (totalSize * feePerByte).toLong()
        val totalFee = BigDecimal(totalFeeSatoshis).divide(
            BigDecimal(BitcoinConstants.SATOSHIS_PER_BTC),
            8,
            RoundingMode.HALF_UP
        )

        // Sweep calculation: Amount = Balance - Fee
        val maxAmount = (balance - totalFee).setScale(8, RoundingMode.DOWN)

        logger.d(
            "MaxCalculation",
            "Bitcoin Max: Balance=$balance | Fee=$totalFee (${totalFeeSatoshis} sats) | Max=$maxAmount | Inputs=${allUtxos.size}, Size=$totalSize, Rate=$feePerByte"
        )

        return if (maxAmount > BigDecimal.ZERO) {
            Result.Success(
                MaxAmountResult(
                    maxAmount,
                    totalFee,
                    totalFeeSatoshis,
                    totalSize,
                    allUtxos.size
                )
            )
        } else {
            Result.Error("Insufficient balance to cover fees")
        }
    }
}
