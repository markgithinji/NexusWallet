package com.example.nexuswallet.feature.settings.ui.auth

sealed interface AuthUiEffect {
    data object Authenticated : AuthUiEffect
}