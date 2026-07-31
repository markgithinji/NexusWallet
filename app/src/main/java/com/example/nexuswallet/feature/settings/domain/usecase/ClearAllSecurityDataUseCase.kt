package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClearAllSecurityDataUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val vaultRepository: VaultRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(): Result<Unit> {
        securityRepository.clearAll()
        vaultRepository.clearVault()
        keyStoreRepository.clearKey()
        logger.d("ClearAllSecurityDataUseCase", "Successfully cleared all security data")
        return Result.Success(Unit)
    }
}