package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.settings.domain.model.AuthMethod
import com.example.nexuswallet.feature.settings.domain.model.AuthStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAuthStatusUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(): Result<AuthStatus> {
        val pinSet = securityPreferencesRepository.getPinHash() != null
        val biometricEnabled = securityPreferencesRepository.isBiometricEnabled()

        val availableMethods = buildList {
            if (pinSet) add(AuthMethod.PIN)
            if (biometricEnabled) add(AuthMethod.BIOMETRIC)
        }

        val authStatus = AuthStatus(
            isPinSet = pinSet,
            isBiometricEnabled = biometricEnabled,
            availableMethods = availableMethods,
            isAnyAuthEnabled = pinSet || biometricEnabled
        )

        logger.d(
            "GetAuthStatusUseCase",
            "Auth status retrieved: PIN set=$pinSet, Biometric enabled=$biometricEnabled"
        )
        return Result.Success(authStatus)
    }
}