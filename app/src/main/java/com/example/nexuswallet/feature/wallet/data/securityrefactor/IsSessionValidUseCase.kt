package com.example.nexuswallet.feature.wallet.data.securityrefactor
import com.example.nexuswallet.feature.coin.Result

interface IsSessionValidUseCase {
    suspend operator fun invoke(): Result<Boolean>
}