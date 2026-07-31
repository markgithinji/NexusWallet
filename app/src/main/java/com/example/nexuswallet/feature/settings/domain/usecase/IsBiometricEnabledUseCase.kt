package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsBiometricEnabledUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val logger: Logger
) {
    operator fun invoke(): Flow<Boolean> =
        securityRepository.observeBiometricEnabled()
            .onStart {
                logger.d("IsBiometricEnabledUseCase", "Starting biometric enabled flow")
            }
            .onEach { isEnabled ->
                logger.d("IsBiometricEnabledUseCase", "Biometric enabled: $isEnabled")
            }
}