package com.example.nexuswallet.feature.authentication.domain

import com.example.nexuswallet.feature.wallet.domain.WalletBackup

enum class AuthAction {
    VIEW_WALLET,
    SEND_TRANSACTION,
    VIEW_PRIVATE_KEY,
    BACKUP_WALLET
}

enum class AuthMethod {
    PIN,
    BIOMETRIC
}

sealed class EncryptionResult {
    data class Success(val encryptedData: String) : EncryptionResult()
    data class Error(val exception: Exception) : EncryptionResult()
}

sealed class BackupResult {
    data class Success(val backup: WalletBackup) : BackupResult()
    data class Error(val exception: Exception) : BackupResult()
}

sealed class RestoreResult {
    data class Success(val backup: WalletBackup) : RestoreResult()
    data class Error(val exception: Exception) : RestoreResult()
}

sealed class SecurityState {
    object IDLE : SecurityState()
    object ENCRYPTING : SecurityState()
    object DECRYPTING : SecurityState()
    object BACKING_UP : SecurityState()
    object RESTORING : SecurityState()
    data class ERROR(val message: String) : SecurityState()
}

sealed class PrivateKeyResult {
    data class Success(val privateKey: String) : PrivateKeyResult()
    data class Error(val exception: Exception? = null) : PrivateKeyResult()
}