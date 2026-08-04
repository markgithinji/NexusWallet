package com.example.nexuswallet.feature.solana.ui

import com.example.nexuswallet.feature.core.domain.model.TransactionResult

sealed class SolanaSendEffect {
    data class TransactionResultEffect(val result: TransactionResult) : SolanaSendEffect()
}
