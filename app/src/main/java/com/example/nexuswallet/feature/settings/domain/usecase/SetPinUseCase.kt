package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.authentication.data.util.PinHasher
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetPinUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val pinHasher: PinHasher,
    private val logger: Logger
) {
    suspend operator fun invoke(pin: String): Result<Boolean> {
        val pinHash = pinHasher.hashPin(pin)
        securityPreferencesRepository.storePinHash(pinHash)
        
        // Verify storage success
        val success = securityPreferencesRepository.getPinHash() == pinHash
        
        return if (success) {
            logger.d("SetPinUseCase", "PIN set successfully")
            Result.Success(true)
        } else {
            logger.e("SetPinUseCase", "PIN hash verification failed")
            Result.Error("Failed to verify PIN storage")
        }
    }
}