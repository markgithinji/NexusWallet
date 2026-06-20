package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
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

    suspend operator fun invoke(walletId: String): List<String>? {
        return try {
            val (encryptedMnemonicHex, iv) = securityPreferencesRepository.getEncryptedMnemonic(walletId) 
                ?: return null.also { logger.w(tag, "No encrypted mnemonic found for wallet: $walletId") }
            
            val decryptedBytes = keyStoreRepository.decrypt(encryptedMnemonicHex.decodeHex(), iv)
            val mnemonicString = String(decryptedBytes, Charsets.UTF_8)
            
            // Clear decrypted bytes immediately
            decryptedBytes.fill(0)
            
            mnemonicString.split(" ")
        } catch (e: Exception) {
            logger.e(tag, "Failed to decrypt mnemonic for wallet: $walletId", e)
            null
        }
    }
}
