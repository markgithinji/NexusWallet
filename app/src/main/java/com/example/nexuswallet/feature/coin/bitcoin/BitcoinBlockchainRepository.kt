package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import java.math.BigDecimal
import com.example.nexuswallet.feature.coin.Result

interface BitcoinBlockchainRepository {
    suspend fun getBalance(
        address: String,
        network: BitcoinNetwork = BitcoinNetwork.MAINNET
    ): Result<BigDecimal>

    suspend fun getFeeEstimate(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        inputCount: Int = 1,
        outputCount: Int = 2
    ): Result<BitcoinFeeEstimate>

    suspend fun broadcastTransaction(
        signedHex: String,
        network: BitcoinNetwork = BitcoinNetwork.MAINNET
    ): Result<String>


    suspend fun getTransactionStatus(
        txid: String,
        network: BitcoinNetwork
    ): Result<TransactionStatus>


    suspend fun getAddressTransactions(
        address: String,
        network: BitcoinNetwork = BitcoinNetwork.MAINNET
    ): Result<List<BitcoinTransactionDto>>


    suspend fun createAndSignTransaction(
        fromKey: ECKey,
        toAddress: String,
        satoshis: Long,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: BitcoinNetwork
    ): Result<Transaction>
}