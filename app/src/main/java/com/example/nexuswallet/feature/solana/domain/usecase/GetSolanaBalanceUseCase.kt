package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSolanaBalanceUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger
) {

    suspend operator fun invoke(
        address: String,
        network: SolanaNetwork
    ): Result<BigDecimal> {
        logger.d(TAG, "Fetching balance for $address on $network")
        val result = solanaBlockchainRepository.getBalance(address, network)
        if (result is Result.Error) {
            logger.e(TAG, "Failed to get balance on $network: ${result.message}")
        }
        return result
    }

    companion object {
        private const val TAG = "GetSolanaBalanceUC"
    }
}