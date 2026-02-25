package com.example.nexuswallet.feature.settings.ui

import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClearAllSecurityDataUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository
) {
    suspend operator fun invoke():Result<Unit> {
        return try {
            securityPreferencesRepository.clearAll()
            keyStoreRepository.clearKey()
           Result.Success(Unit)
        } catch (e: Exception) {
           Result.Error("Failed to clear security data: ${e.message}", e)
        }
    }
}

@Singleton
class SetBiometricEnabledUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean):Result<Unit> {
        return try {
            securityPreferencesRepository.setBiometricEnabled(enabled)
           Result.Success(Unit)
        } catch (e: Exception) {
           Result.Error("Failed to set biometric enabled: ${e.message}", e)
        }
    }
}

@Singleton
class IsBiometricEnabledUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke():Result<Boolean> {
        return try {
            Result.Success(securityPreferencesRepository.isBiometricEnabled())
        } catch (e: Exception) {
           Result.Error("Failed to check biometric enabled: ${e.message}", e)
        }
    }
}

@Singleton
class SetPinUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(pin: String):Result<Boolean> {
        return try {
            val pinHash = hashPin(pin)
            securityPreferencesRepository.storePinHash(pinHash)
            val success = securityPreferencesRepository.getPinHash() != null
           Result.Success(success)
        } catch (e: Exception) {
           Result.Error("Failed to set PIN: ${e.message}", e)
        }
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
class IsPinSetUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(): Result<Boolean> {
        return try {
            val isSet = securityPreferencesRepository.getPinHash() != null
            Result.Success(isSet)
        } catch (e: Exception) {
            Result.Error("Failed to check if PIN is set: ${e.message}", e)
        }
    }
}

@Singleton
class ClearPinUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            securityPreferencesRepository.clearPinHash()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to clear PIN: ${e.message}", e)
        }
    }
}

@Singleton
class GetAuthStatusUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    /**
     * Returns the complete authentication status including available methods
     * and whether any authentication is enabled
     */
    suspend operator fun invoke(): Result<AuthStatus> {
        return try {
            val pinSet = securityPreferencesRepository.getPinHash() != null
            val biometricEnabled = securityPreferencesRepository.isBiometricEnabled()

            val availableMethods = buildList {
                if (pinSet) add(AuthMethod.PIN)
                if (biometricEnabled) add(AuthMethod.BIOMETRIC)
            }

            Result.Success(
                AuthStatus(
                    isPinSet = pinSet,
                    isBiometricEnabled = biometricEnabled,
                    availableMethods = availableMethods,
                    isAnyAuthEnabled = pinSet || biometricEnabled
                )
            )
        } catch (e: Exception) {
            Result.Error("Failed to get auth status: ${e.message}", e)
        }
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