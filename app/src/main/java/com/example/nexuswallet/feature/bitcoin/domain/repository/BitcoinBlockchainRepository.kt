package com.example.nexuswallet.feature.bitcoin.domain.repository

import com.example.nexuswallet.feature.bitcoin.data.model.UTXO
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
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
        outputCount: Int,
        network: BitcoinNetwork,
        isSegwit: Boolean = true
    ): Result<BitcoinFeeEstimate>

    suspend fun getUnspentOutputs(
        address: String,
        network: BitcoinNetwork
    ): Result<List<UTXO>>

    fun selectUtxos(
        utxos: List<UTXO>,
        targetSatoshis: Long
    ): List<UTXO>

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
}