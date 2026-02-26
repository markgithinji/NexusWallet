package com.example.nexuswallet.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val ColorScheme.success: Color
    get() = successLight

val ColorScheme.warning: Color
    get() = warningLight

val ColorScheme.info: Color
    get() = infoLight

val ColorScheme.successContainer: Color
    get() = successContainerLight

val ColorScheme.warningContainer: Color
    get() = warningContainerLight

val ColorScheme.infoContainer: Color
    get() = infoContainerLight

// ================== Nexus Wallet Colors - Light Theme ==================
val primaryLight = Color(0xFF3B82F6) // Primary blue from buttons and icons
val onPrimaryLight = Color(0xFFFFFFFF) // White text on primary
val primaryContainerLight = Color(0xFFE5F0FF) // Light blue container background
val onPrimaryContainerLight = Color(0xFF1E40AF) // Dark blue text on container

val secondaryLight = Color(0xFF10B981) // Green from success states and send
val onSecondaryLight = Color(0xFFFFFFFF) // White text on secondary
val secondaryContainerLight = Color(0xFFD1FAE5) // Light green container
val onSecondaryContainerLight = Color(0xFF047857) // Dark green text

val tertiaryLight = Color(0xFF8B5CF6) // Purple from swap action
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFEDE9FE)
val onTertiaryContainerLight = Color(0xFF6D28D9)

// Status colors
val successLight = Color(0xFF10B981) // Green for success
val onSuccessLight = Color(0xFFFFFFFF)
val successContainerLight = Color(0xFFD1FAE5)
val onSuccessContainerLight = Color(0xFF047857)

val warningLight = Color(0xFFF59E0B) // Amber/Orange for warnings
val onWarningLight = Color(0xFFFFFFFF)
val warningContainerLight = Color(0xFFFEF3C7)
val onWarningContainerLight = Color(0xFFB45309)

val errorLight = Color(0xFFEF4444) // Red from error states
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFEE2E2)
val onErrorContainerLight = Color(0xFFB91C1C)

val infoLight = Color(0xFF3B82F6) // Blue for info
val onInfoLight = Color(0xFFFFFFFF)
val infoContainerLight = Color(0xFFE5F0FF)
val onInfoContainerLight = Color(0xFF1E40AF)

// Surface colors
val backgroundLight = Color(0xFFF5F5F7) // Light gray background from screens
val onBackgroundLight = Color(0xFF1F2937) // Dark gray text

val surfaceLight = Color(0xFFFFFFFF) // White cards and surfaces
val onSurfaceLight = Color(0xFF1F2937) // Dark text on surfaces

val surfaceVariantLight = Color(0xFFF9FAFB) // Very light gray for variant surfaces
val onSurfaceVariantLight = Color(0xFF6B7280) // Medium gray text

// Additional colors
val inverseSurfaceLight = Color(0xFF1F2937)
val inverseOnSurfaceLight = Color(0xFFF9FAFB)
val outlineLight = Color(0xFFE5E7EB) // Light gray for dividers and borders
val outlineVariantLight = Color(0xFFD1D5DB) // Slightly darker outline

// Coin-specific colors
val bitcoinLight = Color(0xFFF7931A)
val ethereumLight = Color(0xFF627EEA)
val solanaLight = Color(0xFF00FFA3)
val usdcLight = Color(0xFF2775CA)

// Text colors
val textPrimaryLight = Color(0xFF1F2937) // Almost black
val textSecondaryLight = Color(0xFF6B7280) // Medium gray
val textTertiaryLight = Color(0xFF9CA3AF) // Light gray
val textDisabledLight = Color(0xFFD1D5DB) // Disabled state

// Chart colors
val chartUpLight = successLight
val chartDownLight = errorLight
val chartNeutralLight = Color(0xFF6B7280)

// ====================== Nexus Wallet Colors - Dark Theme ==========================
val primaryDark = Color(0xFF60A5FA) // Lighter blue for dark theme
val onPrimaryDark = Color(0xFF1F2937) // Dark text on primary
val primaryContainerDark = Color(0xFF1E3A8A) // Dark blue container
val onPrimaryContainerDark = Color(0xFFBFDBFE) // Light blue text

val secondaryDark = Color(0xFF34D399) // Lighter green
val onSecondaryDark = Color(0xFF1F2937)
val secondaryContainerDark = Color(0xFF065F46) // Dark green container
val onSecondaryContainerDark = Color(0xFFA7F3D0) // Light green text

val tertiaryDark = Color(0xFFA78BFA) // Lighter purple
val onTertiaryDark = Color(0xFF1F2937)
val tertiaryContainerDark = Color(0xFF5B21B6) // Dark purple container
val onTertiaryContainerDark = Color(0xFFDDD6FE) // Light purple text

// Status colors
val successDark = Color(0xFF34D399) // Lighter green
val onSuccessDark = Color(0xFF1F2937)
val successContainerDark = Color(0xFF065F46)
val onSuccessContainerDark = Color(0xFFA7F3D0)

val warningDark = Color(0xFFFBBF24) // Lighter amber
val onWarningDark = Color(0xFF1F2937)
val warningContainerDark = Color(0xFF92400E)
val onWarningContainerDark = Color(0xFFFDE68A)

val errorDark = Color(0xFFF87171) // Lighter red
val onErrorDark = Color(0xFF1F2937)
val errorContainerDark = Color(0xFF991B1B)
val onErrorContainerDark = Color(0xFFFECACA)

val infoDark = Color(0xFF60A5FA) // Lighter blue
val onInfoDark = Color(0xFF1F2937)
val infoContainerDark = Color(0xFF1E3A8A)
val onInfoContainerDark = Color(0xFFBFDBFE)

// Surface colors
val backgroundDark = Color(0xFF111827) // Dark background
val onBackgroundDark = Color(0xFFF9FAFB) // Light text

val surfaceDark = Color(0xFF1F2937) // Dark surfaces/cards
val onSurfaceDark = Color(0xFFF9FAFB) // Light text on surfaces

val surfaceVariantDark = Color(0xFF374151) // Medium dark variant
val onSurfaceVariantDark = Color(0xFFD1D5DB) // Light gray text

// Additional colors
val inverseSurfaceDark = Color(0xFFF9FAFB)
val inverseOnSurfaceDark = Color(0xFF1F2937)
val outlineDark = Color(0xFF4B5563) // Dark gray for dividers
val outlineVariantDark = Color(0xFF6B7280) // Lighter outline for dark theme

// Coin-specific colors (keeping similar but slightly adjusted for dark theme)
val bitcoinDark = Color(0xFFFBBF24) // Adjusted for dark theme visibility
val ethereumDark = Color(0xFF818CF8) // Lighter ethereum color
val solanaDark = Color(0xFF6EE7B7) // Adjusted solana color
val usdcDark = Color(0xFF4895EF) // Lighter USDC color

// Text colors
val textPrimaryDark = Color(0xFFF9FAFB) // Almost white
val textSecondaryDark = Color(0xFFD1D5DB) // Light gray
val textTertiaryDark = Color(0xFF9CA3AF) // Medium gray
val textDisabledDark = Color(0xFF6B7280) // Disabled state

// Chart colors (adjusted for dark theme)
val chartUpDark = successDark
val chartDownDark = errorDark
val chartNeutralDark = Color(0xFF9CA3AF)