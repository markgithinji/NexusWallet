package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import java.math.BigDecimal
import com.example.nexuswallet.feature.coin.Result


interface EthereumBlockchainRepository {
    suspend fun getEthereumBalance(
        address: String,
        network: EthereumNetwork
    ): Result<BigDecimal>

    suspend fun getEthereumTransactions(
        address: String,
        network: EthereumNetwork,
        walletId: String
    ): Result<List<EthereumTransaction>>

    suspend fun getCurrentGasPrice(
        network: EthereumNetwork
    ): Result<GasPrice>

    suspend fun getDynamicFeeEstimate(
        feeLevel: FeeLevel,
        network: EthereumNetwork
    ): Result<EthereumFeeEstimate>

    suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        network: EthereumNetwork
    ): Result<EthereumFeeEstimate>

    suspend fun getEthereumNonce(
        address: String,
        network: EthereumNetwork
    ): Result<Int>

    suspend fun broadcastEthereumTransaction(
        rawTx: String,
        network: EthereumNetwork
    ): Result<BroadcastResult>
}