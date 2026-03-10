package com.example.nexuswallet.feature.authentication.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordAuthenticationUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) {
    private val tag = "RecordAuth"

    suspend operator fun invoke(): Result<Unit> {
        val timestamp = System.currentTimeMillis()
        securityPreferencesRepository.saveLastAuthenticationTime(timestamp)
        logger.d(tag, "Authentication recorded | timestamp=$timestamp")
        return Result.Success(Unit)
    }
}