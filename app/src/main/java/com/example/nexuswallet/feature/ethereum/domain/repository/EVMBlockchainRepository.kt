package com.example.nexuswallet.feature.ethereum.domain.repository

import com.example.nexuswallet.feature.core.domain.model.BroadcastResult
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.data.model.GasPrice
import com.example.nexuswallet.feature.ethereum.domain.model.EVMFeeEstimate
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import org.web3j.crypto.RawTransaction
import java.math.BigDecimal
import java.math.BigInteger

interface EVMBlockchainRepository {
    suspend fun getNativeBalance(
        address: String,
        network: EthereumNetwork
    ): Result<BigDecimal>

    suspend fun getTokenBalance(
        address: String,
        tokenContract: String,
        tokenDecimals: Int,
        network: EthereumNetwork
    ): Result<BigDecimal>

    suspend fun getNativeTransactions(
        address: String,
        network: EthereumNetwork,
        walletId: String,
        evmTokenType: EVMTokenType?
    ): Result<List<NativeETHTransaction>>

    suspend fun getTokenTransactions(
        address: String,
        tokenContract: String,
        network: EthereumNetwork,
        walletId: String,
        evmTokenType: EVMTokenType
    ): Result<List<TokenTransaction>>

    suspend fun createAndSignNativeTransaction(
        fromAddress: String,
        fromPrivateKey: ByteArray,
        toAddress: String,
        amountWei: BigInteger,
        gasPriceWei: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        network: EthereumNetwork
    ): Result<Triple<RawTransaction, String, String>>

    suspend fun createAndSignTokenTransaction(
        fromAddress: String,
        fromPrivateKey: ByteArray,
        toAddress: String,
        amount: BigInteger,
        tokenContract: String,
        tokenDecimals: Int,
        gasPriceWei: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        network: EthereumNetwork,
        evmTokenType: EVMTokenType
    ): Result<Triple<RawTransaction, String, String>>

    suspend fun getCurrentGasPrice(
        network: EthereumNetwork
    ): Result<GasPrice>

    suspend fun getNonce(
        address: String,
        network: EthereumNetwork
    ): Result<BigInteger>

    suspend fun broadcastTransaction(
        signedHex: String,
        network: EthereumNetwork
    ): Result<BroadcastResult>
}