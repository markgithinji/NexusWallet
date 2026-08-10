package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.toHex
import com.example.nexuswallet.feature.core.util.use
import com.example.nexuswallet.feature.settings.domain.model.*
import com.example.nexuswallet.feature.settings.domain.repository.BackupRepository
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.logging.Logger
import kotlinx.coroutines.flow.first
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateBackupUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val vaultRepository: VaultRepository,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(pin: String, cipher: Cipher? = null): Result<ByteArray> {
        logger.d(TAG, "Starting backup creation process")
        return try {
            // 1. Collect all Wallets
            val wallets = walletRepository.observeWallets().first()
            logger.d(TAG, "Collected ${wallets.size} wallets for backup")
            
            // 2. Collect corresponding Vault data for each wallet
            val vaultEntries = wallets.map { wallet ->
                logger.d(TAG, "Processing vault data for wallet: ${wallet.name} (${wallet.id})")
                // Decrypt mnemonic from hardware
                val encryptedMnemonic = vaultRepository.getEncryptedMnemonic(wallet.id) 
                    ?: throw Exception("Mnemonic missing for wallet ${wallet.id}")
                
                val mnemonicResult = if (cipher != null) {
                    keyStoreRepository.decryptWithCipher(cipher, encryptedMnemonic.first.decodeHex())
                } else {
                    keyStoreRepository.decrypt(
                        encryptedMnemonic.first.decodeHex(), 
                        encryptedMnemonic.second
                    )
                }
                
                if (mnemonicResult is Result.Error && mnemonicResult.throwable is HardwareAuthRequiredException) {
                    throw mnemonicResult.throwable
                }
                
                if (mnemonicResult !is Result.Success) {
                    throw Exception("Failed to decrypt mnemonic for wallet ${wallet.id}")
                }
                
                val mnemonicBytes = mnemonicResult.data
                
                val keyTypes = listOf(
                    WalletConstants.KEY_BITCOIN_MAINNET,
                    WalletConstants.KEY_BITCOIN_TESTNET,
                    WalletConstants.KEY_ETHEREUM_MAIN,
                    WalletConstants.KEY_SOLANA_MAINNET,
                    WalletConstants.KEY_SOLANA_DEVNET
                )
                
                val privateKeys = keyTypes.mapNotNull { type ->
                    vaultRepository.getEncryptedPrivateKey(wallet.id, type)?.let { (data, iv) ->
                        // Reuse the same cipher session if it was provided
                        val keyResult = if (cipher != null) {
                             keyStoreRepository.decryptWithCipher(cipher, data.decodeHex())
                        } else {
                            keyStoreRepository.decrypt(data.decodeHex(), iv)
                        }

                        if (keyResult is Result.Success) {
                            val rawKey = keyResult.data
                            val entry = PrivateKeyRawEntry(type, rawKey.toHex())
                            rawKey.fill(0) // Wipe raw key after hexing
                            entry
                        } else null
                    }
                }
                
                val entry = VaultWalletEntry(
                    walletId = wallet.id,
                    mnemonicRaw = mnemonicBytes.toHex(),
                    privateKeys = privateKeys
                )
                mnemonicBytes.fill(0) // Wipe raw mnemonic bytes
                entry
            }
            
            // 3. Collect Settings
            val settings = BackupSettings(
                themeMode = settingsRepository.getThemeMode().name,
                selectedCurrency = settingsRepository.getSelectedCurrency().code,
                privacyModeEnabled = settingsRepository.isPrivacyModeEnabled(),
                requireAuthForSend = settingsRepository.isRequireAuthForSend()
            )
            
            // 4. Create Bundle
            val bundle = BackupBundle(
                wallets = wallets,
                vaultData = vaultEntries,
                settings = settings
            )
            
            // 5. Encrypt Bundle via Repository
            val result = backupRepository.encryptBackup(bundle, pin)
            if (result is Result.Success) {
                logger.d(TAG, "Backup bundle successfully encrypted, size: ${result.data.size} bytes")
            }
            result
            
        } catch (e: HardwareAuthRequiredException) {
            logger.w(TAG, "Biometric authentication required for backup")
            Result.Error("Biometric authentication required", e)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to create backup: ${e.message}", e)
            Result.Error(e.message ?: "Failed to create backup")
        }
    }

    companion object {
        private const val TAG = "CreateBackupUC"
    }
}
