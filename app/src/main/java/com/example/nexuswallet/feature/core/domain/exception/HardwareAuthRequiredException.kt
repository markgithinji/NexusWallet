package com.example.nexuswallet.feature.core.domain.exception

import androidx.biometric.BiometricPrompt

/**
 * Specialized exception to signal that hardware-backed keys are locked 
 * and require biometric authentication.
 */
class HardwareAuthRequiredException(
    val cryptoObject: BiometricPrompt.CryptoObject? = null,
    message: String = "Biometric authentication required"
) : Exception(message)
