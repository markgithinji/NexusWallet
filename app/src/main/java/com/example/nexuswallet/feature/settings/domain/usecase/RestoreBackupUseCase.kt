package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import com.example.nexuswallet.feature.settings.domain.repository.BackupRepository
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreBackupUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val vaultRepository: VaultRepository,
    private val securityRepository: SecurityRepository,
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(backupData: ByteArray, pin: String): Result<Unit> {
        return try {
            // 1. Decrypt Bundle via Repository
            val decryptResult = backupRepository.decryptBackup(backupData, pin)
            if (decryptResult is Result.Error) return Result.Error(decryptResult.message)
            
            val bundle = (decryptResult as Result.Success).data
            
            // 2. Restore Wallets to Database
            bundle.wallets.forEach { wallet ->
                walletRepository.saveWallet(wallet)
            }
            
            // 3. Restore sensitive data to Vault
            bundle.vaultData.forEach { entry ->
                vaultRepository.storeEncryptedMnemonic(
                    entry.walletId, 
                    entry.mnemonic.data, 
                    entry.mnemonic.iv.decodeHex()
                )
                
                entry.privateKeys.forEach { pk ->
                    vaultRepository.storeEncryptedPrivateKey(
                        entry.walletId,
                        pk.keyType,
                        pk.encryptedKey.data,
                        pk.encryptedKey.iv.decodeHex()
                    )
                }
            }
            
            // 4. Restore Application Settings
            securityRepository.setThemeMode(ThemeMode.valueOf(bundle.settings.themeMode))
            securityRepository.setSelectedCurrency(SupportedCurrency.fromCode(bundle.settings.selectedCurrency))
            securityRepository.setPrivacyModeEnabled(bundle.settings.privacyModeEnabled)
            securityRepository.setRequireAuthForSend(bundle.settings.requireAuthForSend)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to restore backup")
        }
    }
}
