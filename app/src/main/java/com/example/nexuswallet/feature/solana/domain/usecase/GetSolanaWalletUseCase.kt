package com.example.nexuswallet.feature.solana.domain.usecase


import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.model.SolanaWalletInfo
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSolanaWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val logger: Logger
) {

    private val tag = "GetSolanaWalletUC"

    suspend operator fun invoke(
        walletId: String,
        network: SolanaNetwork?
    ): Result<SolanaWalletInfo> {
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        // If network specified, get that specific Solana coin
        val solanaCoin = if (network != null) {
            wallet.solanaCoins.find { it.network == network }
        } else {
            // Otherwise get the first one
            wallet.solanaCoins.firstOrNull()
        }

        if (solanaCoin == null) {
            val networkMsg = network?.let { " for $it" } ?: ""
            logger.e(tag, "Solana not enabled$networkMsg for wallet: ${wallet.name}")
            return Result.Error("Solana not enabled${networkMsg} for this wallet")
        }

        logger.d(
            tag,
            "Loaded wallet: ${wallet.name}, address: ${solanaCoin.address.take(8)}..., network: ${solanaCoin.network}"
        )

        return Result.Success(
            SolanaWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = solanaCoin.address,
                network = solanaCoin.network
            )
        )
    }
}