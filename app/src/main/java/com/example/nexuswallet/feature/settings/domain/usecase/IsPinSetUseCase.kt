package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsPinSetUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(): Result<Boolean> {
        val isSet = securityPreferencesRepository.getPinHash() != null
        logger.d("IsPinSetUseCase", "PIN set check: $isSet")
        return Result.Success(isSet)
    }
}
