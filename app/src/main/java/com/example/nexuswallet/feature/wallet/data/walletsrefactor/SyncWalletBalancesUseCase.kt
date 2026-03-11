package com.example.nexuswallet.feature.wallet.data.walletsrefactor
import com.example.nexuswallet.feature.core.util.Result

interface SyncWalletBalancesUseCase {
    suspend operator fun invoke(wallet: Wallet): Result<Unit>
}