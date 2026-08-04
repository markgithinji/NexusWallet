package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClearPinUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(): Result<Unit> {
        settingsRepository.clearPinHash()
        logger.d(TAG, "PIN cleared successfully")
        return Result.Success(Unit)
    }

    companion object {
        private const val TAG = "ClearPinUC"
    }
}