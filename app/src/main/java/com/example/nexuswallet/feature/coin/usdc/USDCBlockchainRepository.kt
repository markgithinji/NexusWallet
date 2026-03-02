package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
//import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
//import com.example.nexuswallet.feature.coin.usdc.domain.USDCFeeEstimate
//import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransaction
//import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
import org.web3j.crypto.RawTransaction
import java.math.BigDecimal
import java.math.BigInteger
//
//interface USDCBlockchainRepository {
//    suspend fun getUSDCBalance(
//        address: String,
//        network: EthereumNetwork
//    ): Result<USDCBalance>
//
//    suspend fun getFeeEstimate(
//        feeLevel: FeeLevel,
//        network: EthereumNetwork
//    ): Result<USDCFeeEstimate>
//
//    suspend fun createAndSignUSDCTransfer(
//        fromAddress: String,
//        fromPrivateKey: String,
//        toAddress: String,
//        amount: BigDecimal,
//        gasPriceWei: BigInteger,
//        nonce: BigInteger,
//        chainId: Long,
//        network: EthereumNetwork
//    ): Result<Triple<RawTransaction, String, String>>
//
//
//    suspend fun broadcastUSDCTransaction(
//        signedHex: String,
//        network: EthereumNetwork
//    ): Result<BroadcastResult>
//
//    suspend fun getNonce(
//        address: String,
//        network: EthereumNetwork
//    ): Result<BigInteger>
//
//    suspend fun getUSDCTransactionHistory(
//        walletId: String,
//        address: String,
//        network: EthereumNetwork
//    ): Result<List<USDCTransaction>>
//}