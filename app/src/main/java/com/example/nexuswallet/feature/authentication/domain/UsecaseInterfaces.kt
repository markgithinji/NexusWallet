package com.example.nexuswallet.feature.authentication.domain

import com.example.nexuswallet.feature.coin.Result

interface VerifyPinUseCase {
    suspend operator fun invoke(pin: String): Result<Boolean>
}

interface RecordAuthenticationUseCase {
    suspend operator fun invoke(): Result<Unit>
}