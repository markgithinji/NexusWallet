package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.use
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

    suspend operator fun invoke(walletId: String, cipher: Cipher? = null): Result<List<String>> {
        val (encryptedMnemonicHex, iv) = vaultRepository.getEncryptedMnemonic(walletId)
            ?: return Result.Error("No encrypted mnemonic found for wallet: $walletId")

        val decryptionResult = if (cipher != null) {
            keyStoreRepository.decryptWithCipher(cipher, encryptedMnemonicHex.decodeHex())
        } else {
            keyStoreRepository.decrypt(encryptedMnemonicHex.decodeHex(), iv)
        }

        if (decryptionResult is Result.Error) {
            return decryptionResult
        }

        val decryptedBytes = (decryptionResult as Result.Success).data
        return decryptedBytes.use { bytes ->
            try {
                val mnemonicString = String(bytes, Charsets.UTF_8)
                Result.Success(mnemonicString.split(" "))
            } catch (e: Exception) {
                logger.e(TAG, "Failed to parse mnemonic for wallet: $walletId", e)
                Result.Error("Failed to parse mnemonic: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "GetMnemonicUC"
    }
}
