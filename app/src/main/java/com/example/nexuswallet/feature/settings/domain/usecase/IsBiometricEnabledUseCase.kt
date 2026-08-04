package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsBiometricEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {
    operator fun invoke(): Flow<Boolean> =
        settingsRepository.observeBiometricEnabled()
            .onStart {
                logger.d(TAG, "Starting biometric enabled flow")
            }
            .onEach { isEnabled ->
                logger.d(TAG, "Biometric enabled: $isEnabled")
            }

    companion object {
        private const val TAG = "IsBiometricEnabledUC"
    }
}