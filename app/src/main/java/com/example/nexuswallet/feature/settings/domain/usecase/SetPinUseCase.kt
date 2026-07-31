package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.core.data.util.PinHasher
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetPinUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val pinHasher: PinHasher,
    private val logger: Logger
) {
    suspend operator fun invoke(pin: String): Result<Boolean> {
        val pinHash = pinHasher.hashPin(pin)
        securityRepository.storePinHash(pinHash)
        
        // Verify storage success
        val success = securityRepository.getPinHash() == pinHash
        
        return if (success) {
            logger.d("SetPinUseCase", "PIN set successfully")
            Result.Success(true)
        } else {
            logger.e("SetPinUseCase", "PIN hash verification failed")
            Result.Error("Failed to verify PIN storage")
        }
    }
}