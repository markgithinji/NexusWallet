package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinWalletInfo
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class GetBitcoinWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val logger: Logger
) {

    private val tag = "GetBitcoinWalletUC"

    suspend operator fun invoke(
        walletId: String,
        network: BitcoinNetwork?
    ): Result<BitcoinWalletInfo> {
        logger.d(tag, "Looking up Bitcoin wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        // If network specified, get that specific Bitcoin coin
        val bitcoinCoin = if (network != null) {
            wallet.bitcoinCoins.find { it.network == network }
        } else {
            // Otherwise get the first one
            wallet.bitcoinCoins.firstOrNull()
        }

        if (bitcoinCoin == null) {
            val networkMsg = network?.let { " for $it" } ?: ""
            logger.e(tag, "Bitcoin not enabled$networkMsg for wallet: ${wallet.name}")
            return Result.Error("Bitcoin not enabled${networkMsg} for this wallet")
        }

        logger.d(
            tag,
            "Found wallet: ${wallet.name} | Address: ${bitcoinCoin.address.take(8)}... | Network: ${bitcoinCoin.network}"
        )

        return Result.Success(
            BitcoinWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = bitcoinCoin.address,
                network = bitcoinCoin.network
            )
        )
    }
}