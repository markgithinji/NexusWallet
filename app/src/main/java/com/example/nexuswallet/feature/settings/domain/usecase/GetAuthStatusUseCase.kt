package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.settings.domain.model.AuthMethod
import com.example.nexuswallet.feature.settings.domain.model.AuthStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAuthStatusUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(): Result<AuthStatus> {
        val pinSet = settingsRepository.getPinHash() != null
        val biometricEnabled = settingsRepository.isBiometricEnabled()
        val privacyModeEnabled = settingsRepository.isPrivacyModeEnabled()
        val requireAuthForSend = settingsRepository.isRequireAuthForSend()

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
            TAG,
            "Auth status retrieved: PIN set=$pinSet, Biometric enabled=$biometricEnabled, Privacy mode=$privacyModeEnabled, Require Auth for Send=$requireAuthForSend"
        )
        return Result.Success(authStatus)
    }

    companion object {
        private const val TAG = "GetAuthStatusUC"
    }
}