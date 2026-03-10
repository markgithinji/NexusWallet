package com.example.nexuswallet.feature.coin.bitcoin.domain.repository

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import java.math.BigDecimal

interface BitcoinBlockchainRepository {
    suspend fun getBalance(
        address: String,
        network: BitcoinNetwork
    ): Result<BigDecimal>

    suspend fun getFeeEstimate(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        inputCount: Int = 1,
        outputCount: Int = 2
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
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: BitcoinNetwork
    ): Result<Transaction>
}