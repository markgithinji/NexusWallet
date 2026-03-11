package com.example.nexuswallet.feature.bitcoin.domain.repository

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import java.math.BigDecimal

interface BitcoinBlockchainRepository {
    suspend fun getBalance(
        address: String,
        network: BitcoinNetwork
    ): Result<BigDecimal>

    suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        inputCount: Int,
        outputCount: Int
    ): Result<BitcoinFeeEstimate>

    suspend fun broadcastTransaction(
        signedHex: String,
        network: BitcoinNetwork
    ): Result<String>


    suspend fun getTransactionStatus(
        txid: String,
        network: BitcoinNetwork
    ): Result<TransactionStatus>


    suspend fun getAddressTransactions(
        walletId: String,
        address: String,
        network: BitcoinNetwork
    ): Result<List<BitcoinTransaction>>


    suspend fun createAndSignTransaction(
        fromKey: ECKey,
        toAddress: String,
        satoshis: Long,
        feeLevel: FeeLevel,
        network: BitcoinNetwork
    ): Result<Transaction>
}