package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.coin.ethereum.domain.model.EthereumWalletInfo
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.NativeETH
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetEthereumWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val logger: Logger
) {

    private val tag = "GetEthereumWalletUC"

    suspend operator fun invoke(walletId: String): Result<EthereumWalletInfo> {
        logger.d(tag, "Looking up Ethereum wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        // Find the first NativeETH token
        val nativeEth = wallet.evmTokens.filterIsInstance<NativeETH>().firstOrNull()
        if (nativeEth == null) {
            logger.e(tag, "Ethereum not enabled for wallet: ${wallet.name}")
            return Result.Error("Ethereum not enabled for this wallet")
        }

        logger.d(
            tag,
            "Found wallet: ${wallet.name}, Address: ${nativeEth.address.take(8)}..., Network: ${nativeEth.network.displayName}"
        )

        return Result.Success(
            EthereumWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = nativeEth.address,
                network = nativeEth.network
            )
        )
    }
}