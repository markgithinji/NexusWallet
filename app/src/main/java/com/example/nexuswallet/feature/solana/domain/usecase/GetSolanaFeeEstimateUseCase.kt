package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSolanaFeeEstimateUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger
) {

    suspend operator fun invoke(
        feeLevel: FeeLevel,
        network: SolanaNetwork,
        fromAddress: String? = null,
        toAddress: String? = null,
        lamports: Long? = null,
        tokenMint: String? = null
    ): Result<SolanaFeeEstimate> {
        val result = solanaBlockchainRepository.getFeeEstimate(
            feeLevel = feeLevel,
            network = network,
            fromAddress = fromAddress,
            toAddress = toAddress,
            lamports = lamports,
            tokenMint = tokenMint
        )

        when (result) {
            is Result.Success -> {
                val fee = result.data
                logger.d(
                    TAG,
                    "Fee Result: Total=${fee.feeSol} SOL (${fee.feeLamports} lamports) | PriorityRate=${fee.priorityFeeRate} | CU=${fee.computeUnits} | Token=${tokenMint != null}"
                )
            }

            is Result.Error -> {
                logger.e(TAG, "Fee Error: ${result.message} on $network")
            }

            else -> {}
        }

        return result
    }

    companion object {
        private const val TAG = "GetSolanaFeeUC"
    }
}