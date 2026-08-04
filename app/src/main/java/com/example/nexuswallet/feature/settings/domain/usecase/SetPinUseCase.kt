package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.core.data.util.PinHasher
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetPinUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pinHasher: PinHasher,
    private val logger: Logger
) {
    suspend operator fun invoke(pin: String): Result<Boolean> {
        val pinHash = pinHasher.hashPin(pin)
        settingsRepository.storePinHash(pinHash)
        
        // Verify storage success
        val success = settingsRepository.getPinHash() == pinHash
        
        return if (success) {
            logger.d(TAG, "PIN set successfully")
            Result.Success(true)
        } else {
            logger.e(TAG, "PIN hash verification failed")
            Result.Error("Failed to verify PIN storage")
        }
    }

    companion object {
        private const val TAG = "SetPinUC"
    }
}