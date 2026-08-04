package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.core.data.util.PinHasher
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerifyPinUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pinHasher: PinHasher,
    private val logger: Logger
) {

    suspend operator fun invoke(pin: String): Result<Boolean> {
        val startTime = System.currentTimeMillis()

        val storedHash = settingsRepository.getPinHash()
        if (storedHash == null) {
            logger.d(TAG, "No PIN set")
            return Result.Success(false)
        }

        val isValid = pinHasher.verifyPin(pin, storedHash)
        val duration = System.currentTimeMillis() - startTime

        if (isValid) {
            logger.d(TAG, "PIN verified | duration=${duration}ms")
            return Result.Success(true)
        } else {
            logger.w(TAG, "PIN verification failed | duration=${duration}ms")
            return Result.Success(false)
        }
    }

    companion object {
        private const val TAG = "VerifyPin"
    }
}