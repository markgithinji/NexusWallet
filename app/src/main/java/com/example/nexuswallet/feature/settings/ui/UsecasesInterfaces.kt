package com.example.nexuswallet.feature.settings.ui
import com.example.nexuswallet.feature.coin.Result

interface ClearAllSecurityDataUseCase {
    suspend operator fun invoke(): Result<Unit>
}

interface SetBiometricEnabledUseCase {
    suspend operator fun invoke(enabled: Boolean): Result<Unit>
}

interface IsBiometricEnabledUseCase {
    suspend operator fun invoke(): Result<Boolean>
}

interface SetPinUseCase {
    suspend operator fun invoke(pin: String): Result<Boolean>
}

interface IsPinSetUseCase {
    suspend operator fun invoke(): Result<Boolean>
}

interface ClearPinUseCase {
    suspend operator fun invoke(): Result<Unit>
}

interface GetAuthStatusUseCase {
    suspend operator fun invoke(): Result<AuthStatus>
}