package com.example.nexuswallet.feature.coin.ethereum.data

import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EVMFeeEstimate
import com.example.nexuswallet.feature.coin.ethereum.GasPrice
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TokenType
import org.web3j.crypto.RawTransaction
import java.math.BigDecimal
import java.math.BigInteger
import com.example.nexuswallet.feature.coin.Result

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
        tokenExternalId: String?
    ): Result<List<NativeETHTransaction>>

    suspend fun getTokenTransactions(
        address: String,
        tokenContract: String,
        network: EthereumNetwork,
        walletId: String,
        tokenExternalId: String
    ): Result<List<TokenTransaction>>

    suspend fun createAndSignNativeTransaction(
        fromAddress: String,
        fromPrivateKey: String,
        toAddress: String,
        amountWei: BigInteger,
        gasPriceWei: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        network: EthereumNetwork
    ): Result<Triple<RawTransaction, String, String>>

    suspend fun createAndSignTokenTransaction(
        fromAddress: String,
        fromPrivateKey: String,
        toAddress: String,
        amount: BigInteger,
        tokenContract: String,
        tokenDecimals: Int,
        gasPriceWei: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        network: EthereumNetwork,
        tokenType: TokenType
    ): Result<Triple<RawTransaction, String, String>>

    suspend fun getCurrentGasPrice(
        network: EthereumNetwork
    ): Result<GasPrice>

    suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        network: EthereumNetwork,
        isToken: Boolean
    ): Result<EVMFeeEstimate>

    suspend fun getNonce(
        address: String,
        network: EthereumNetwork
    ): Result<BigInteger>

    suspend fun broadcastTransaction(
        signedHex: String,
        network: EthereumNetwork
    ): Result<BroadcastResult>
}