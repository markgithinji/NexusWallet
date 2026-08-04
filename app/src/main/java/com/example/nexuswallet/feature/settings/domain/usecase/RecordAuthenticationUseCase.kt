package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordAuthenticationUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {

    suspend operator fun invoke(): Result<Unit> {
        val timestamp = System.currentTimeMillis()
        settingsRepository.saveLastAuthenticationTime(timestamp)
        logger.d(TAG, "Authentication recorded | timestamp=$timestamp")
        return Result.Success(Unit)
    }

    companion object {
        private const val TAG = "RecordAuth"
    }
}