package com.example.nexuswallet.feature.settings.ui.security

import com.example.nexuswallet.feature.settings.domain.model.AuthMethod

data class SecurityUiState(
    val isBiometricEnabled: Boolean = false,
    val isPinSet: Boolean = false,
    val isPrivacyModeEnabled: Boolean = false,
    val isRequireAuthForSend: Boolean = false,
    val availableAuthMethods: List<AuthMethod> = emptyList(),
    val isAnyAuthEnabled: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    val isNotificationRationaleSilenced: Boolean = false,
    val hasRequestedNotificationPermission: Boolean = false
)