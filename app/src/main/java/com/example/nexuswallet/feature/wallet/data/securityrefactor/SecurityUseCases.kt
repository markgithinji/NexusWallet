package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.authentication.domain.AuthAction
import com.example.nexuswallet.feature.coin.Result
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrieveMnemonicUseCase @Inject constructor(
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(walletId: String): Result<List<String>> {
        return try {
            val encryptedData = securityPreferencesRepository.getEncryptedMnemonic(walletId)
                ?: return Result.Error("Mnemonic not found for wallet: $walletId")

            val (encryptedHex, iv) = encryptedData
            val decrypted = keyStoreRepository.decryptString(encryptedHex, iv.toHex())

            Result.Success(decrypted.split(" "))
        } catch (e: Exception) {
            Result.Error("Failed to retrieve mnemonic: ${e.message}", e)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

@Singleton
class SecureMnemonicUseCase @Inject constructor(
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(walletId: String, mnemonic: List<String>): Result<Unit> {
        return try {
            val mnemonicString = mnemonic.joinToString(" ")
            val (encryptedHex, ivHex) = keyStoreRepository.encryptString(mnemonicString)
            securityPreferencesRepository.storeEncryptedMnemonic(
                walletId = walletId,
                encryptedMnemonic = encryptedHex,
                iv = ivHex.hexToBytes()
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to secure mnemonic: ${e.message}", e)
        }
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

@Singleton
class StorePrivateKeyUseCase @Inject constructor(
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyValidator: KeyValidator
) {
    suspend operator fun invoke(
        walletId: String,
        keyType: String,
        privateKey: String
    ): Result<Unit> {
        return try {
            if (!keyValidator.validatePrivateKey(privateKey, keyType)) {
                return Result.Error("Invalid private key format for $keyType")
            }

            val (encryptedHex, ivHex) = keyStoreRepository.encryptString(privateKey)
            securityPreferencesRepository.storeEncryptedPrivateKey(
                walletId = walletId,
                keyType = keyType,
                encryptedKey = encryptedHex,
                iv = ivHex.hexToBytes()
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to store $keyType private key: ${e.message}", e)
        }
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

@Singleton
class GetPrivateKeyForSigningUseCase @Inject constructor(
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(
        walletId: String,
        keyType: String = "ETH_PRIVATE_KEY",
        requireAuth: Boolean = true,
        action: AuthAction = AuthAction.SEND_TRANSACTION
    ): Result<String> {
        return try {
            // Get encrypted private key
            val encryptedData =
                securityPreferencesRepository.getEncryptedPrivateKey(walletId, keyType)
                    ?: return Result.Error("Private key not found for $keyType")

            val (encryptedHex, iv) = encryptedData

            // Decrypt
            val privateKey = keyStoreRepository.decryptString(encryptedHex, iv.toHex())

            Result.Success(privateKey)
        } catch (e: SecurityException) {
            Result.Error("Security exception: ${e.message}", e)
        } catch (e: Exception) {
            Result.Error("Failed to get private key for signing: ${e.message}", e)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

@Singleton
class HasPrivateKeyUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(walletId: String, keyType: String = "ETH_PRIVATE_KEY"): Boolean {
        return securityPreferencesRepository.getEncryptedPrivateKey(walletId, keyType) != null
    }
}

@Singleton
class ClearAllSecurityDataUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            securityPreferencesRepository.clearAll()
            keyStoreRepository.clearKey()
            sessionManager.clearSession()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to clear security data: ${e.message}", e)
        }
    }
}

@Singleton
class IsKeyStoreAvailableUseCase @Inject constructor(
    private val keyStoreRepository: KeyStoreRepository
) {
    operator fun invoke(): Boolean = keyStoreRepository.isKeyStoreAvailable()
}

@Singleton
class SetBiometricEnabledUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> {
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
    suspend operator fun invoke(): Result<Boolean> {
        return try {
            Result.Success(securityPreferencesRepository.isBiometricEnabled())
        } catch (e: Exception) {
            Result.Error("Failed to check biometric enabled: ${e.message}", e)
        }
    }
}

@Singleton
class SetPinUseCase @Inject constructor(
    private val pinManager: PinManager
) {
    suspend operator fun invoke(pin: String): Result<Boolean> {
        return try {
            val result = pinManager.setPin(pin)
            Result.Success(result)
        } catch (e: Exception) {
            Result.Error("Failed to set PIN: ${e.message}", e)
        }
    }
}

@Singleton
class VerifyPinUseCase @Inject constructor(
    private val pinManager: PinManager
) {
    suspend operator fun invoke(pin: String): Result<Boolean> {
        return try {
            val result = pinManager.verifyPin(pin)
            Result.Success(result)
        } catch (e: Exception) {
            Result.Error("Failed to verify PIN: ${e.message}", e)
        }
    }
}

@Singleton
class IsPinSetUseCase @Inject constructor(
    private val pinManager: PinManager
) {
    suspend operator fun invoke(): Result<Boolean> {
        return try {
            Result.Success(pinManager.isPinSet())
        } catch (e: Exception) {
            Result.Error("Failed to check if PIN is set: ${e.message}", e)
        }
    }
}

@Singleton
class ClearPinUseCase @Inject constructor(
    private val pinManager: PinManager
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            pinManager.clearPin()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to clear PIN: ${e.message}", e)
        }
    }
}

@Singleton
class GetAvailableAuthMethodsUseCase @Inject constructor(
    private val pinManager: PinManager,
    private val isBiometricEnabledUseCase: IsBiometricEnabledUseCase
) {
    suspend operator fun invoke(): Result<List<AuthMethod>> {
        return try {
            val methods = mutableListOf<AuthMethod>()

            if (pinManager.isPinSet()) {
                methods.add(AuthMethod.PIN)
            }

            when (val bioResult = isBiometricEnabledUseCase()) {
                is Result.Success -> {
                    if (bioResult.data) {
                        methods.add(AuthMethod.BIOMETRIC)
                    }
                }

                is Result.Error -> {
                    // TODO: Log error but don't fail the whole operation
                }

                Result.Loading -> { /* Ignore */
                }
            }

            Result.Success(methods)
        } catch (e: Exception) {
            Result.Error("Failed to get available auth methods: ${e.message}", e)
        }
    }
}

@Singleton
class IsAnyAuthEnabledUseCase @Inject constructor(
    private val pinManager: PinManager,
    private val isBiometricEnabledUseCase: IsBiometricEnabledUseCase
) {
    suspend operator fun invoke(): Result<Boolean> {
        return try {
            val pinSet = pinManager.isPinSet()

            val bioEnabled = when (val bioResult = isBiometricEnabledUseCase()) {
                is Result.Success -> bioResult.data
                is Result.Error -> false
                Result.Loading -> false
            }

            Result.Success(pinSet || bioEnabled)
        } catch (e: Exception) {
            Result.Error("Failed to check if any auth is enabled: ${e.message}", e)
        }
    }
}

@Singleton
class SetSessionTimeoutUseCase @Inject constructor(
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(seconds: Int): Result<Unit> {
        return try {
            sessionManager.setSessionTimeout(seconds)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to set session timeout: ${e.message}", e)
        }
    }
}

@Singleton
class IsSessionValidUseCase @Inject constructor(
    private val sessionManager: SessionManager
) {
    operator fun invoke(): Boolean = sessionManager.isSessionValid()
}

@Singleton
class IsAuthenticationRequiredUseCase @Inject constructor(
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(action: AuthAction = AuthAction.VIEW_WALLET): Result<Boolean> {
        return try {
            Result.Success(sessionManager.isAuthenticationRequired(action))
        } catch (e: Exception) {
            Result.Error("Failed to check if authentication is required: ${e.message}", e)
        }
    }
}

@Singleton
class RecordAuthenticationUseCase @Inject constructor(
    private val sessionManager: SessionManager
) {
    operator fun invoke(): Result<Unit> {
        return try {
            sessionManager.recordAuthentication()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to record authentication: ${e.message}", e)
        }
    }
}

@Singleton
class ClearSessionUseCase @Inject constructor(
    private val sessionManager: SessionManager
) {
    operator fun invoke(): Result<Unit> {
        return try {
            sessionManager.clearSession()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to clear session: ${e.message}", e)
        }
    }
}

enum class AuthMethod {
    PIN,
    BIOMETRIC
}