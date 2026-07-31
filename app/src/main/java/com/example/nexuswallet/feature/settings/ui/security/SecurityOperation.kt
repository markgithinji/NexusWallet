package com.example.nexuswallet.feature.settings.ui.security

sealed class SecurityOperation {
    object IDLE : SecurityOperation()
    object UPDATING : SecurityOperation()
    object BACKING_UP : SecurityOperation()
    object RESTORING : SecurityOperation()
}