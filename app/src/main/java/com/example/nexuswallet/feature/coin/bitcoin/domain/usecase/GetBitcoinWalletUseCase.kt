package com.example.nexuswallet.feature.coin.bitcoin.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SendValidationResult
import com.example.nexuswallet.feature.coin.bitcoin.data.toDomain
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinWalletInfo
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.DumpedPrivateKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.Utils
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import java.math.BigDecimal
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