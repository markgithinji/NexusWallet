package com.example.nexuswallet.feature.settings.ui

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.logging.Logger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClearAllSecurityDataUseCaseImpl @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) : ClearAllSecurityDataUseCase {
    override suspend operator fun invoke(): Result<Unit> {
        securityPreferencesRepository.clearAll()
        keyStoreRepository.clearKey()
        logger.d("ClearAllSecurityDataUseCase", "Successfully cleared all security data")
        return Result.Success(Unit)
    }
}

@Singleton
class SetBiometricEnabledUseCaseImpl @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : SetBiometricEnabledUseCase {
    override suspend operator fun invoke(enabled: Boolean): Result<Unit> {
        securityPreferencesRepository.setBiometricEnabled(enabled)
        logger.d("SetBiometricEnabledUseCase", "Biometric enabled set to: $enabled")
        return Result.Success(Unit)
    }
}

@Singleton
class IsBiometricEnabledUseCaseImpl @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : IsBiometricEnabledUseCase {
    override suspend operator fun invoke(): Result<Boolean> {
        val isEnabled = securityPreferencesRepository.isBiometricEnabled()
        logger.d("IsBiometricEnabledUseCase", "Biometric enabled check: $isEnabled")
        return Result.Success(isEnabled)
    }
}

@Singleton
class SetPinUseCaseImpl @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : SetPinUseCase {
    override suspend operator fun invoke(pin: String): Result<Boolean> {
        val pinHash = hashPin(pin)
        securityPreferencesRepository.storePinHash(pinHash)
        val success = securityPreferencesRepository.getPinHash() != null
        logger.d("SetPinUseCase", "PIN set successfully: $success")
        return Result.Success(success)
    }

    private fun hashPin(pin: String): String {
        val salt = generateSalt()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$pin${salt.toHex()}".toByteArray())
        return "${hash.toHex()}:${salt.toHex()}"
    }

    private fun generateSalt(): ByteArray {
        return ByteArray(16).also { SecureRandom().nextBytes(it) }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

@Singleton
class IsPinSetUseCaseImpl @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : IsPinSetUseCase {
    override suspend operator fun invoke(): Result<Boolean> {
        val isSet = securityPreferencesRepository.getPinHash() != null
        logger.d("IsPinSetUseCase", "PIN set check: $isSet")
        return Result.Success(isSet)
    }
}

@Singleton
class ClearPinUseCaseImpl @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : ClearPinUseCase {
    override suspend operator fun invoke(): Result<Unit> {
        securityPreferencesRepository.clearPinHash()
        logger.d("ClearPinUseCase", "PIN cleared successfully")
        return Result.Success(Unit)
    }
}

@Singleton
class GetAuthStatusUseCaseImpl @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : GetAuthStatusUseCase {
    override suspend operator fun invoke(): Result<AuthStatus> {
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

        logger.d("GetAuthStatusUseCase", "Auth status retrieved: PIN set=$pinSet, Biometric enabled=$biometricEnabled")
        return Result.Success(authStatus)
    }
}

enum class AuthMethod {
    PIN,
    BIOMETRIC
}

data class AuthStatus(
    val isPinSet: Boolean,
    val isBiometricEnabled: Boolean,
    val availableMethods: List<AuthMethod>,
    val isAnyAuthEnabled: Boolean
)