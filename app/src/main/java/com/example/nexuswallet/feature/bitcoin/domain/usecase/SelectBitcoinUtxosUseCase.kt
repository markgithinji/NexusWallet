package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.bitcoin.data.model.UTXO
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelectBitcoinUtxosUseCase @Inject constructor() {
    
    /**
     * Selects UTXOs while accounting for the fee each input adds to the transaction.
     * 
     * @param utxos The list of available UTXOs.
     * @param targetSatoshis The amount the user wants to send (excluding fees).
     * @param feePerByte The current fee rate in sat/vB.
     * @param outputCount The number of outputs (usually 2: recipient + change).
     */
    operator fun invoke(
        utxos: List<UTXO>,
        targetSatoshis: Long,
        feePerByte: Double,
        outputCount: Int = BitcoinConstants.DEFAULT_OUTPUT_COUNT
    ): List<UTXO> {
        val selected = mutableListOf<UTXO>()
        var totalSelected = 0L
        val sortedUtxos = utxos.sortedByDescending { it.value.value }
        
        for (utxo in sortedUtxos) {
            selected.add(utxo)
            totalSelected += utxo.value.value
            
            // Recalculate fee for current input count
            val estSize = BitcoinConstants.BASE_TX_SIZE + 
                         (selected.size * BitcoinConstants.BYTES_PER_INPUT) + 
                         (outputCount * BitcoinConstants.BYTES_PER_OUTPUT)
            val currentFee = (estSize * feePerByte).toLong()
            
            if (totalSelected >= (targetSatoshis + currentFee)) {
                return selected
            }
        }
        
        // If we reach here, we didn't find enough funds to cover amount + fee
        return emptyList()
    }
}
