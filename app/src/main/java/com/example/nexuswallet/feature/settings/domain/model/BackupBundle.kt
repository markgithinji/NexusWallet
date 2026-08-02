package com.example.nexuswallet.feature.settings.domain.model

import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import kotlinx.serialization.Serializable

@Serializable
data class BackupBundle(
    val wallets: List<Wallet>,
    val vaultData: List<VaultWalletEntry>,
    val settings: BackupSettings,
    val backupTimestamp: Long = System.currentTimeMillis(),
    val version: Int = 1
)

@Serializable
data class VaultWalletEntry(
    val walletId: String,
    val mnemonic: EncryptedData,
    val privateKeys: List<PrivateKeyEntry>
)

@Serializable
data class PrivateKeyEntry(
    val keyType: String,
    val encryptedKey: EncryptedData
)

@Serializable
data class EncryptedData(
    val data: String, // Base64 or Hex
    val iv: String    // Hex
)

@Serializable
data class BackupSettings(
    val themeMode: String,
    val selectedCurrency: String,
    val privacyModeEnabled: Boolean,
    val requireAuthForSend: Boolean
)
