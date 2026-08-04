package com.example.nexuswallet.feature.core.domain.model

sealed class TransactionResult {
    data class Success(
        val txHash: String,
        val explorerUrl: String? = null
    ) : TransactionResult()
    
    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : TransactionResult()
    
    object Loading : TransactionResult()
}
