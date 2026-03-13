package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.core.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClearAllSecurityDataUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(): Result<Unit> {
        securityPreferencesRepository.clearAll()
        keyStoreRepository.clearKey()
        logger.d("ClearAllSecurityDataUseCase", "Successfully cleared all security data")
        return Result.Success(Unit)
    }
}