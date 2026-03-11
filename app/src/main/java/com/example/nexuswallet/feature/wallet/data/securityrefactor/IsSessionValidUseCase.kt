package com.example.nexuswallet.feature.wallet.data.securityrefactor
import com.example.nexuswallet.feature.core.util.Result

interface IsSessionValidUseCase {
    suspend operator fun invoke(): Result<Boolean>
}