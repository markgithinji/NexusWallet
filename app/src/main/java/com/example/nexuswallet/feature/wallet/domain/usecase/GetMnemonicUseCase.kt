package com.example.nexuswallet.feature.wallet.domain.usecase

import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricPrompt
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetMnemonicUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) {
    private val tag = "GetMnemonicUC"

    suspend operator fun invoke(walletId: String): Result<List<String>> {
        return try {
            val (encryptedMnemonicHex, iv) = securityPreferencesRepository.getEncryptedMnemonic(walletId) 
                ?: return Result.Error("No encrypted mnemonic found for wallet: $walletId")
            
            val decryptedBytes = try {
                keyStoreRepository.decrypt(encryptedMnemonicHex.decodeHex(), iv)
            } catch (e: Exception) {
                // Check if it's an auth-required exception (sometimes wrapped)
                val isAuthRequired = e is UserNotAuthenticatedException || e.cause is UserNotAuthenticatedException
                
                if (isAuthRequired) {
                    val cipher = keyStoreRepository.getDecryptionCipher(iv)
                    return Result.Error(
                        message = "Authentication required",
                        throwable = HardwareAuthRequiredException(BiometricPrompt.CryptoObject(cipher))
                    )
                }
                throw e
            }

            try {
                val mnemonicString = String(decryptedBytes, Charsets.UTF_8)
                Result.Success(mnemonicString.split(" "))
            } finally {
                // Clear decrypted bytes immediately
                decryptedBytes.fill(0)
            }
        } catch (e: Exception) {
            logger.e(tag, "Failed to decrypt mnemonic for wallet: $walletId", e)
            Result.Error("Failed to decrypt mnemonic: ${e.message}", e)
        }
    }
}
