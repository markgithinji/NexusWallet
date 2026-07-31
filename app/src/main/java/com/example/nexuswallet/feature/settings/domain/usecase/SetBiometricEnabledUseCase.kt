package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetBiometricEnabledUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> {
        securityRepository.setBiometricEnabled(enabled)
        logger.d("SetBiometricEnabledUseCase", "Biometric enabled set to: $enabled")
        return Result.Success(Unit)
    }
}
