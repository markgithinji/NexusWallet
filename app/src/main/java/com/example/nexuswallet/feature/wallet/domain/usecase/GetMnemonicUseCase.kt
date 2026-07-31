package com.example.nexuswallet.feature.wallet.domain.usecase

import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricPrompt
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.logging.Logger
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetMnemonicUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) {
    private val tag = "GetMnemonicUC"

    suspend operator fun invoke(walletId: String, cipher: Cipher? = null): Result<List<String>> {
        return try {
            val (encryptedMnemonicHex, iv) = vaultRepository.getEncryptedMnemonic(walletId)
                ?: return Result.Error("No encrypted mnemonic found for wallet: $walletId")
            
            val decryptedBytes = if (cipher != null) {
                keyStoreRepository.decryptWithCipher(cipher, encryptedMnemonicHex.decodeHex())
            } else {
                try {
                    keyStoreRepository.decrypt(encryptedMnemonicHex.decodeHex(), iv)
                } catch (e: Exception) {
                    val isAuthRequired = e is UserNotAuthenticatedException ||
                            e.cause is UserNotAuthenticatedException ||
                            e is javax.crypto.IllegalBlockSizeException && e.message?.contains("user not authenticated", true) == true

                    if (isAuthRequired) {
                        val authCipher = try {
                            keyStoreRepository.getDecryptionCipher(iv)
                        } catch (authEx: Exception) {
                            null
                        }
                        
                        val cryptoObject = authCipher?.let { BiometricPrompt.CryptoObject(it) }
                        
                        return Result.Error(
                            message = "Authentication required",
                            throwable = HardwareAuthRequiredException(cryptoObject)
                        )
                    }
                    throw e
                }
            }

            try {
                val mnemonicString = String(decryptedBytes, Charsets.UTF_8)
                Result.Success(mnemonicString.split(" "))
            } finally {
                decryptedBytes.fill(0)
            }
        } catch (e: Exception) {
            logger.e(tag, "Failed to decrypt mnemonic for wallet: $walletId", e)
            Result.Error("Failed to decrypt mnemonic: ${e.message}", e)
        }
    }
}
