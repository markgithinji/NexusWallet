package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSolanaBalanceUseCase @Inject constructor(
    private val solanaBlockchainRepository: com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository,
    private val logger: Logger
) {

    private val tag = "GetSolanaBalanceUC"

    suspend operator fun invoke(
        address: String,
        network: SolanaNetwork
    ): Result<BigDecimal> {
        logger.d(tag, "Fetching balance for $address on $network")
        val result = solanaBlockchainRepository.getBalance(address, network)
        if (result is Result.Error) {
            logger.e(tag, "Failed to get balance on $network: ${result.message}")
        }
        return result
    }
}