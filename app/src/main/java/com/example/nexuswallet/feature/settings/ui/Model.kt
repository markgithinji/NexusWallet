package com.example.nexuswallet.feature.settings.ui

sealed class SecurityState {
    object IDLE : SecurityState()
    object ENCRYPTING : SecurityState()
    object DECRYPTING : SecurityState()
    object BACKING_UP : SecurityState()
    object RESTORING : SecurityState()
    data class ERROR(val message: String) : SecurityState()
}