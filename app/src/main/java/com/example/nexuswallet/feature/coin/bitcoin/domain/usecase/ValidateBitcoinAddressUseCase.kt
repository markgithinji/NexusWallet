package com.example.nexuswallet.feature.coin.bitcoin.domain.usecase

import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import org.bitcoinj.core.Address
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidateBitcoinAddressUseCase @Inject constructor(
    private val logger: Logger
) {

    private val tag = "ValidateBitcoinUC"

    fun invoke(address: String, network: BitcoinNetwork): Boolean {
        return try {
            val params = when (network) {
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
            }
            Address.fromString(params, address)
            logger.d(tag, "Valid $network address: ${address.take(8)}...")
            true
        } catch (e: Exception) {
            logger.e(tag, "Invalid $network address: ${address.take(8)}...")
            false
        }
    }
}