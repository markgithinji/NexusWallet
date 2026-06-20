package com.example.nexuswallet.feature.settings.ui

import com.example.nexuswallet.feature.settings.domain.model.AuthMethod

data class SecurityUiState(
    val isBiometricEnabled: Boolean = false,
    val isPinSet: Boolean = false,
    val isPrivacyModeEnabled: Boolean = false,
    val availableAuthMethods: List<AuthMethod> = emptyList(),
    val isAnyAuthEnabled: Boolean = false
)