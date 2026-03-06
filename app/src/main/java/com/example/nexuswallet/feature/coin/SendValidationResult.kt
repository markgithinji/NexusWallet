package com.example.nexuswallet.feature.coin

// Common validation result for all coin types
data class SendValidationResult(
    val isValid: Boolean = false,
    val addressError: String? = null,
    val amountError: String? = null,
    val balanceError: String? = null,
    val selfSendError: String? = null,
    val gasError: String? = null,  // For EVM
    val networkError: String? = null
)