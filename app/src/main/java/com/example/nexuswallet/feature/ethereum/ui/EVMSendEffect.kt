package com.example.nexuswallet.feature.ethereum.ui

import com.example.nexuswallet.feature.core.domain.model.TransactionResult

sealed class EVMSendEffect {
    data class TransactionResultEffect(val result: TransactionResult) : EVMSendEffect()
}
