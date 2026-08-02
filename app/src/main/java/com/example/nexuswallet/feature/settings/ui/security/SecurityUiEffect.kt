package com.example.nexuswallet.feature.settings.ui.security

sealed class SecurityUiEffect {
    data class ShowSnackbar(val message: String) : SecurityUiEffect()
    data class SaveBackupFile(val data: ByteArray, val fileName: String) : SecurityUiEffect()
    object SelectBackupFile : SecurityUiEffect()
    object RestoreSuccess : SecurityUiEffect()
}
