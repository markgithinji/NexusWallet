package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetBiometricEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> {
        settingsRepository.setBiometricEnabled(enabled)
        logger.d(TAG, "Biometric enabled set to: $enabled")
        return Result.Success(Unit)
    }

    companion object {
        private const val TAG = "SetBiometricEnabledUC"
    }
}
