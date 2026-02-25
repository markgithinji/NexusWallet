package com.example.nexuswallet.feature.settings.ui

import com.example.nexuswallet.feature.wallet.data.securityrefactor.AuthMethod

data class SecurityUiState(
    val isBiometricEnabled: Boolean = false,
    val isPinSet: Boolean = false,
    val availableAuthMethods: List<AuthMethod> = emptyList(),
    val isAnyAuthEnabled: Boolean = false
)