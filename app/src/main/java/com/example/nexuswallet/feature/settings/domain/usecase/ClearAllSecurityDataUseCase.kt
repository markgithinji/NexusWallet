package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClearAllSecurityDataUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val vaultRepository: VaultRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val walletRepository: WalletRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(): Result<Unit> = withContext(ioDispatcher) {
        securityRepository.clearAll()
        vaultRepository.clearVault()
        keyStoreRepository.clearKey()
        walletRepository.clearAllData()
        logger.d(TAG, "Successfully cleared all security data")
        Result.Success(Unit)
    }

    companion object {
        private const val TAG = "ClearAllSecurityDataUC"
    }
}