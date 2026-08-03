package com.example.nexuswallet.feature.settings.domain.model

import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import kotlinx.serialization.Serializable

@Serializable
data class BackupBundle(
    val wallets: List<Wallet>,
    val vaultData: List<VaultWalletEntry>,
    val settings: BackupSettings,
    val backupTimestamp: Long = System.currentTimeMillis(),
    val version: Int = 2 // Updated version
)

@Serializable
data class VaultWalletEntry(
    val walletId: String,
    val mnemonicRaw: String, // Hex encoded RAW mnemonic bytes
    val privateKeys: List<PrivateKeyRawEntry>
)

@Serializable
data class PrivateKeyRawEntry(
    val keyType: String,
    val keyRaw: String // Hex encoded RAW private key bytes
)

@Serializable
data class BackupSettings(
    val themeMode: String,
    val selectedCurrency: String,
    val privacyModeEnabled: Boolean,
    val requireAuthForSend: Boolean
)
