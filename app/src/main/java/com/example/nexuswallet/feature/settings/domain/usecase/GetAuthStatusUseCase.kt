package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.settings.domain.model.AuthMethod
import com.example.nexuswallet.feature.settings.domain.model.AuthStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAuthStatusUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(): Result<AuthStatus> {
        val pinSet = securityRepository.getPinHash() != null
        val biometricEnabled = securityRepository.isBiometricEnabled()
        val privacyModeEnabled = securityRepository.isPrivacyModeEnabled()
        val requireAuthForSend = securityRepository.isRequireAuthForSend()

        val availableMethods = buildList {
            if (pinSet) add(AuthMethod.PIN)
            if (biometricEnabled) add(AuthMethod.BIOMETRIC)
        }

        val authStatus = AuthStatus(
            isPinSet = pinSet,
            isBiometricEnabled = biometricEnabled,
            isPrivacyModeEnabled = privacyModeEnabled,
            isRequireAuthForSend = requireAuthForSend,
            availableMethods = availableMethods,
            isAnyAuthEnabled = pinSet || biometricEnabled
        )

        logger.d(
            "GetAuthStatusUseCase",
            "Auth status retrieved: PIN set=$pinSet, Biometric enabled=$biometricEnabled, Privacy mode=$privacyModeEnabled, Require Auth for Send=$requireAuthForSend"
        )
        return Result.Success(authStatus)
    }
}