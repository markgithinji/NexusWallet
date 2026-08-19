package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.bitcoin.data.model.UTXO
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelectBitcoinUtxosUseCase @Inject constructor(
    private val logger: Logger
) {
    
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
            
            // Recalculate fee for current input count using accurate per-input estimation
            val totalInputSize = selected.fold(0L) { acc, item ->
                acc + when {
                    org.bitcoinj.script.ScriptPattern.isP2WPKH(item.script) -> BitcoinConstants.BYTES_PER_INPUT_SEGWIT
                    org.bitcoinj.script.ScriptPattern.isP2SH(item.script) -> BitcoinConstants.BYTES_PER_INPUT_P2SH
                    else -> BitcoinConstants.BYTES_PER_INPUT
                }
            }
            
            val hasSegwit = selected.any { org.bitcoinj.script.ScriptPattern.isP2WPKH(it.script) }
            val baseSize = if (hasSegwit) 11L else BitcoinConstants.BASE_TX_SIZE
            val outputSize = if (hasSegwit) (outputCount * BitcoinConstants.BYTES_PER_OUTPUT_SEGWIT) else (outputCount * BitcoinConstants.BYTES_PER_OUTPUT)
            
            val estSize = baseSize + totalInputSize + outputSize
            val currentFee = (estSize * feePerByte).toLong()
            
            if (totalSelected >= (targetSatoshis + currentFee)) {
                logger.d("SelectUtxos", "Selected ${selected.size} inputs for target $targetSatoshis | Fee: $currentFee sats (Rate: $feePerByte, Size: $estSize vB, Outputs: $outputCount)")
                return selected
            }
        }
        
        logger.w("SelectUtxos", "Failed to find sufficient funds for target $targetSatoshis + fee (Total Selected: $totalSelected)")
        return emptyList()
    }
}
