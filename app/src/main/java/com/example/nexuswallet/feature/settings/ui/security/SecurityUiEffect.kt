package com.example.nexuswallet.feature.settings.ui.security

sealed class SecurityUiEffect {
    data class ShowSnackbar(val message: String) : SecurityUiEffect()
}