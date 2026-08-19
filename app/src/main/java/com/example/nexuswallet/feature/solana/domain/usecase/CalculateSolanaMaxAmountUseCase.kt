package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalculateSolanaMaxAmountUseCase @Inject constructor(
    private val solanaRepository: SolanaBlockchainRepository,
    private val logger: Logger
) {
    data class MaxAmountResult(
        val amount: BigDecimal,
        val feeSol: BigDecimal
    )

    suspend operator fun invoke(
        address: String,
        network: SolanaNetwork,
        feeLevel: FeeLevel,
        isNativeSol: Boolean,
        tokenMint: String?
    ): Result<MaxAmountResult> {
        // 1. Fresh Balance Fetch
        val solResult = solanaRepository.getBalance(address, network)
        if (solResult !is Result.Success) return Result.Error("Failed to fetch SOL balance")

        val assetResult = if (isNativeSol) {
            solResult
        } else {
            if (tokenMint == null) return Result.Error("Token mint missing")
            solanaRepository.getTokenBalance(address, tokenMint, network)
        }
        if (assetResult !is Result.Success) return Result.Error("Failed to fetch asset balance")
        val currentAssetBalance = assetResult.data

        // 2. Precise Fee Calculation
        val feeResult = solanaRepository.getFeeEstimate(
            feeLevel = feeLevel,
            network = network,
            fromAddress = address,
            toAddress = null, // Unknown recipient for Max
            lamports = 0L,
            tokenMint = tokenMint
        )

        if (feeResult !is Result.Success) return Result.Error("Failed to estimate fees")

        val feeEstimate = feeResult.data
        val totalFeeSol = BigDecimal(feeEstimate.feeSol)

        // 3. Atomic Sweep Calculation
        val maxAmount = if (isNativeSol) {
            (currentAssetBalance - totalFeeSol).setScale(9, RoundingMode.DOWN)
        } else {
            currentAssetBalance
        }

        logger.d(
            "MaxCalculation",
            "Solana Max: Asset=${if (isNativeSol) "SOL" else tokenMint} | Balance=$currentAssetBalance | Fee=$totalFeeSol | Max=$maxAmount"
        )

        return if (maxAmount > BigDecimal.ZERO) {
            Result.Success(MaxAmountResult(maxAmount, totalFeeSol))
        } else {
            Result.Error("Insufficient balance to cover fees")
        }
    }
}
