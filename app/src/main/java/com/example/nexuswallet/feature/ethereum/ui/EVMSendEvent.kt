package com.example.nexuswallet.feature.ethereum.ui

import com.example.nexuswallet.feature.core.domain.model.FeeLevel

sealed class EVMSendEvent {
    data class ToAddressChanged(val address: String) : EVMSendEvent()
    data class AmountChanged(val amount: String) : EVMSendEvent()
    data class NoteChanged(val note: String) : EVMSendEvent()
    data class FeeLevelChanged(val feeLevel: FeeLevel) : EVMSendEvent()
    object Validate : EVMSendEvent()
    object ClearError : EVMSendEvent()
    data class ToggleFiatMode(val isFiatMode: Boolean) : EVMSendEvent()
    object UseMax : EVMSendEvent()
}