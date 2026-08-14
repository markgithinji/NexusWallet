package com.example.nexuswallet.feature.bitcoin.domain

import com.example.nexuswallet.feature.bitcoin.data.model.UTXO
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.core.TransactionWitness
import org.bitcoinj.crypto.TransactionSignature
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptPattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A dedicated component for building and signing Bitcoin transactions.
 * Following the "AnySigner" pattern, this contains only pure logic and no network access.
 */
@Singleton
class BitcoinTransactionSigner @Inject constructor() {

    fun sign(
        fromKey: ECKey,
        toAddress: String,
        amountSatoshis: Long,
        changeAddress: String,
        feeSatoshis: Long,
        selectedUtxos: List<UTXO>,
        networkParameters: NetworkParameters
    ): Transaction {
        val totalSelectedSatoshis = selectedUtxos.sumOf { it.value.value }

        // 1. Construct Transaction
        val tx = Transaction(networkParameters)
        // Note: version is a field in bitcoinj, but in some Kotlin mappings it might be read-only property or need setVersion
        try {
            val field = tx.javaClass.getDeclaredField("version")
            field.isAccessible = true
            field.setLong(tx, 2L)
        } catch (_: Exception) {
            // Fallback if field access fails
        }

        // Add Outputs
        val outputs = mutableListOf<TransactionOutputData>()
        outputs.add(
            TransactionOutputData(
                Coin.valueOf(amountSatoshis),
                Address.fromString(networkParameters, toAddress)
            )
        )

        val changeValue = totalSelectedSatoshis - amountSatoshis - feeSatoshis
        if (changeValue >= BitcoinConstants.DUST_LIMIT) {
            outputs.add(
                TransactionOutputData(
                    Coin.valueOf(changeValue),
                    Address.fromString(networkParameters, changeAddress)
                )
            )
        }

        // Sort Outputs (BIP-69)
        outputs.sortWith(OutputComparator())
        for (out in outputs) {
            tx.addOutput(out.value, out.address)
        }

        // Add Inputs (Sorted BIP-69)
        val sortedUtxos = selectedUtxos.sortedWith(
            compareBy(
                { it.outPoint.hash.toString() },
                { it.outPoint.index })
        )
        for (utxo in sortedUtxos) {
            // Note: passing null for parent prevents automatic addition to tx.inputs
            val input = TransactionInput(
                networkParameters,
                null,
                utxo.script.program,
                utxo.outPoint,
                utxo.value
            )
            tx.addInput(input)
        }

        // 2. Sign each input
        for (i in 0 until tx.inputs.size) {
            val input = tx.getInput(i.toLong())
            val utxo = sortedUtxos[i]

            if (ScriptPattern.isP2WPKH(utxo.script)) {
                // Native SegWit Signing (BIP-143)
                // For P2WPKH, the scriptCode is: OP_DUP OP_HASH160 <pubkey_hash> OP_EQUALVERIFY OP_CHECKSIG
                val pubkeyHash = utxo.script.program.copyOfRange(2, 22)
                val scriptCode = ScriptBuilder.createP2PKHOutputScript(pubkeyHash)

                // Ensure key is compressed for SegWit
                val compressedKey =
                    if (fromKey.isCompressed) fromKey else ECKey.fromPrivate(fromKey.privKey, true)

                val hash = tx.hashForWitnessSignature(
                    i,
                    scriptCode,
                    utxo.value,
                    Transaction.SigHash.ALL,
                    false
                )
                val sig = compressedKey.sign(hash)
                val txSig = TransactionSignature(sig, Transaction.SigHash.ALL, false)

                input.witness = TransactionWitness.redeemP2WPKH(txSig, compressedKey)
                input.scriptSig = ScriptBuilder.createEmpty()
            } else {
                // Legacy Signing
                val hash = tx.hashForSignature(i, utxo.script, Transaction.SigHash.ALL, false)
                val sig = fromKey.sign(hash)
                val txSig = TransactionSignature(sig, Transaction.SigHash.ALL, false)
                input.scriptSig = ScriptBuilder.createInputScript(txSig, fromKey)
            }
        }

        tx.verify()
        return tx
    }

    private data class TransactionOutputData(val value: Coin, val address: Address)

    private class OutputComparator : Comparator<TransactionOutputData> {
        override fun compare(o1: TransactionOutputData, o2: TransactionOutputData): Int {
            val v1 = o1.value.value
            val v2 = o2.value.value
            if (v1 != v2) return v1.compareTo(v2)
            return o1.address.toString().compareTo(o2.address.toString())
        }
    }
}
