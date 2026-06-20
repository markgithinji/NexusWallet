package com.example.nexuswallet.feature.wallet.domain.model

/**
 * Structured error information for a specific blockchain or token sync operation.
 */
data class ChainSyncError(
    val network: Network,
    val message: String,
    val assetSymbol: String? = null
)

/**
 * A report containing the results of a multi-chain sync operation.
 */
data class SyncReport(
    val walletId: String,
    val errors: List<ChainSyncError> = emptyList()
) {
    val isSuccessful: Boolean get() = errors.isEmpty()
    val hasErrors: Boolean get() = errors.isNotEmpty()
}
