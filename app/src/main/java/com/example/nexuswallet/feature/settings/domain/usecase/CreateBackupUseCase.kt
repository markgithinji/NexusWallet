package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants
import com.example.nexuswallet.feature.core.util.toHex
import com.example.nexuswallet.feature.settings.domain.model.*
import com.example.nexuswallet.feature.settings.domain.repository.BackupRepository
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateBackupUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val vaultRepository: VaultRepository,
    private val securityRepository: SecurityRepository,
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(pin: String): Result<ByteArray> {
        return try {
            // 1. Collect all Wallets
            val wallets = walletRepository.observeWallets().first()
            
            // 2. Collect corresponding Vault data for each wallet
            val vaultEntries = wallets.map { wallet ->
                val mnemonic = vaultRepository.getEncryptedMnemonic(wallet.id) 
                    ?: throw Exception("Mnemonic missing for wallet ${wallet.id}")
                
                val keyTypes = listOf(
                    WalletConstants.KEY_BITCOIN_MAINNET,
                    WalletConstants.KEY_BITCOIN_TESTNET,
                    WalletConstants.KEY_ETHEREUM_MAIN,
                    WalletConstants.KEY_SOLANA_MAINNET,
                    WalletConstants.KEY_SOLANA_DEVNET
                )
                
                val privateKeys = keyTypes.mapNotNull { type ->
                    vaultRepository.getEncryptedPrivateKey(wallet.id, type)?.let { (data, iv) ->
                        PrivateKeyEntry(type, EncryptedData(data, iv.toHex()))
                    }
                }
                
                VaultWalletEntry(
                    walletId = wallet.id,
                    mnemonic = EncryptedData(mnemonic.first, mnemonic.second.toHex()),
                    privateKeys = privateKeys
                )
            }
            
            // 3. Collect Settings
            val settings = BackupSettings(
                themeMode = securityRepository.getThemeMode().name,
                selectedCurrency = securityRepository.getSelectedCurrency().code,
                privacyModeEnabled = securityRepository.isPrivacyModeEnabled(),
                requireAuthForSend = securityRepository.isRequireAuthForSend()
            )
            
            // 4. Create Bundle
            val bundle = BackupBundle(
                wallets = wallets,
                vaultData = vaultEntries,
                settings = settings
            )
            
            // 5. Encrypt Bundle via Repository
            backupRepository.encryptBackup(bundle, pin)
            
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to create backup")
        }
    }
}
