package com.example.nexuswallet.feature.settings.ui

sealed class SecurityUiEffect {
    data class ShowSnackbar(val message: String) : SecurityUiEffect()
}