package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.authentication.domain.AuthAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    private var lastAuthenticationTime = Long.MIN_VALUE
    private var sessionTimeout = 5 * 60 * 1000L // 5 minutes default

    init {
        CoroutineScope(Dispatchers.IO).launch {
            sessionTimeout = securityPreferencesRepository.getSessionTimeout() * 1000L
        }
    }

    fun isSessionValid(): Boolean {
        if (lastAuthenticationTime == Long.MIN_VALUE) return false
        return System.currentTimeMillis() - lastAuthenticationTime < sessionTimeout
    }

    suspend fun isAuthenticationRequired(action: AuthAction = AuthAction.VIEW_WALLET): Boolean {
        if (isSessionValid()) return false

        val pinSet = securityPreferencesRepository.getPinHash() != null
        val biometricEnabled = securityPreferencesRepository.isBiometricEnabled()

        return pinSet || biometricEnabled
    }

    fun recordAuthentication() {
        lastAuthenticationTime = System.currentTimeMillis()
    }

    fun clearSession() {
        lastAuthenticationTime = Long.MIN_VALUE
    }

    suspend fun setSessionTimeout(seconds: Int) {
        sessionTimeout = seconds * 1000L
        securityPreferencesRepository.saveSessionTimeout(seconds)
    }

    suspend fun getSessionTimeout(): Long {
        return securityPreferencesRepository.getSessionTimeout() * 1000L
    }

    fun getLastAuthenticationTime(): Long = lastAuthenticationTime
}