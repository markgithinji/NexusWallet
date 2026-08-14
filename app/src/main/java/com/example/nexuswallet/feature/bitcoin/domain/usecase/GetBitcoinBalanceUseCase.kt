package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetBitcoinBalanceUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger
) {

    suspend operator fun invoke(
        address: String,
        network: BitcoinNetwork,
        xpub: String? = null
    ): Result<BigDecimal> {
        logger.d(TAG, "Fetching aggregate balance for wallet on $network")

        val targetAddresses = mutableListOf(address)
        
        // If xpub is provided, also check the SegWit counterpart to provide aggregate balance
        xpub?.let {
            try {
                val params = when (network) {
                    BitcoinNetwork.Mainnet -> MainNetParams.get()
                    BitcoinNetwork.Testnet -> TestNet3Params.get()
                }
                val masterKey = DeterministicKey.deserializeB58(it, params)
                val segwitAddr = SegwitAddress.fromKey(params, masterKey).toString()
                if (segwitAddr != address) {
                    targetAddresses.add(segwitAddr)
                }
            } catch (_: Exception) {}
        }

        var totalBalance = BigDecimal.ZERO
        
        for (addr in targetAddresses) {
            when (val result = bitcoinBlockchainRepository.getBalance(addr, network)) {
                is Result.Success -> {
                    totalBalance = totalBalance.add(result.data)
                }
                is Result.Error -> {
                    // If one fails, we return error to avoid showing partial/incorrect balance
                    return Result.Error("Failed to fetch balance for $addr: ${result.message}")
                }
                else -> {}
            }
        }

        logger.d(TAG, "Aggregate Balance: $totalBalance BTC")
        return Result.Success(totalBalance)
    }

    companion object {
        private const val TAG = "GetBitcoinBalanceUC"
    }
}
