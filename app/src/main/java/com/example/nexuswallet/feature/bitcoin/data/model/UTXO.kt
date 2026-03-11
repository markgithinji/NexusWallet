package com.example.nexuswallet.feature.bitcoin.data.model

import org.bitcoinj.core.Coin
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.script.Script

data class UTXO(
    val outPoint: TransactionOutPoint,
    val value: Coin,
    val script: Script
)