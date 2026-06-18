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
        val success = securityPreferencesRepository.getPinHash() != null
        logger.d("SetPinUseCase", "PIN set successfully: $success")
        return Result.Success(success)
    }
}