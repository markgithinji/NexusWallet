package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsPinSetUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) {
    operator fun invoke(): Flow<Boolean> =
        securityPreferencesRepository.observePinHash()
            .map { pinHash -> pinHash != null }
            .onStart {
                logger.d("IsPinSetUseCase", "Starting PIN set flow")
            }
            .onEach { isSet ->
                logger.d("IsPinSetUseCase", "PIN set: $isSet")
            }
}
