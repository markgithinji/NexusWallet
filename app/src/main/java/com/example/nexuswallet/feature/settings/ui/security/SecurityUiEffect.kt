package com.example.nexuswallet.feature.settings.ui.security

import com.example.nexuswallet.feature.core.util.UiText

sealed class SecurityUiEffect {
    data class ShowSnackbar(val message: UiText) : SecurityUiEffect()
    data class SaveBackupFile(val data: ByteArray, val fileName: String) : SecurityUiEffect()
    object SelectBackupFile : SecurityUiEffect()
    object RestoreSuccess : SecurityUiEffect()
}
