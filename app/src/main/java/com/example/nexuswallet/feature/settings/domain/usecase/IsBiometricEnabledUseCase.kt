package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsBiometricEnabledUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(): Result<Boolean> {
        val isEnabled = securityPreferencesRepository.isBiometricEnabled()
        logger.d("IsBiometricEnabledUseCase", "Biometric enabled check: $isEnabled")
        return Result.Success(isEnabled)
    }
}