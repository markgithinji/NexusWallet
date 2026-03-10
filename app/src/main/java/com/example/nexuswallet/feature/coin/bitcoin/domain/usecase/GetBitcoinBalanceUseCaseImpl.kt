package com.example.nexuswallet.feature.coin.bitcoin.domain.usecase

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetBitcoinBalanceUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger
) {

    private val tag = "GetBitcoinBalanceUC"

    suspend operator fun invoke(
        address: String,
        network: BitcoinNetwork
    ): Result<BigDecimal> {
        logger.d(tag, "Fetching balance for ${address.take(8)}... on $network")

        return when (val result = bitcoinBlockchainRepository.getBalance(address, network)) {
            is Result.Success -> {
                logger.d(tag, "Balance: ${result.data} BTC")
                Result.Success(result.data)
            }

            is Result.Error -> {
                logger.e(tag, "Failed to get balance: ${result.message}")
                Result.Error(result.message)
            }

            else -> Result.Error("Unknown error getting balance")
        }
    }
}