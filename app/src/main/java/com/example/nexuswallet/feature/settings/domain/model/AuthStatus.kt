package com.example.nexuswallet.feature.settings.domain.model

data class AuthStatus(
    val isPinSet: Boolean,
    val isBiometricEnabled: Boolean,
    val isPrivacyModeEnabled: Boolean,
    val availableMethods: List<AuthMethod>,
    val isAnyAuthEnabled: Boolean
)