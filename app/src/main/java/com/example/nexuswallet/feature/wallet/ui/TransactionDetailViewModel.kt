package com.example.nexuswallet.feature.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.GetTransactionDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.TransactionDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val getTransactionDetailUseCase: GetTransactionDetailUseCase,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
    private val logger: Logger
) : ViewModel() {

    private val tag = "TransactionDetailVM"

    data class TransactionDetailState(
        val transaction: TransactionDetail? = null,
        val formattedAmount: String = "",
        val formattedFee: String = "",
        val formattedTime: String = "",
        val formattedUsd: String = "$0.00",
        val usdValue: Double = 0.0,
        val isLoading: Boolean = false,
        val error: String? = null,
        val isRefreshing: Boolean = false
    )

    private val _state = MutableStateFlow(TransactionDetailState())
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TransactionDetailEffect>()
    val effects: SharedFlow<TransactionDetailEffect> = _effects.asSharedFlow()

    sealed class TransactionDetailEffect {
        data class ShowError(val message: String) : TransactionDetailEffect()
        data class CopyToClipboard(val text: String, val label: String) : TransactionDetailEffect()
        object ShareTransaction : TransactionDetailEffect()
        data class OpenExplorer(val url: String) : TransactionDetailEffect()
    }

    fun loadTransactionDetail(
        walletId: String,
        transactionId: String,
        coinType: CoinType
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (val result = getTransactionDetailUseCase(walletId, transactionId, coinType)) {
                is Result.Success -> {
                    val transaction = result.data

                    // Create a dummy transaction object for formatting
                    val dummyTx = when (coinType) {
                        CoinType.BITCOIN -> createDummyBitcoinTx(transaction)
                        CoinType.SOLANA -> createDummySolanaTx(transaction)
                        CoinType.ETHEREUM, CoinType.USDC -> createDummyEVMTx(transaction, coinType)
                    }

                    val displayInfo = formatTransactionDisplayUseCase(dummyTx, coinType)

                    // Calculate USD value TODO: integrate market repository
                    val usdValue = calculateUSDValue(transaction)
                    val formattedUsd = NumberFormat.getCurrencyInstance(Locale.US).format(usdValue)

                    _state.update {
                        it.copy(
                            transaction = transaction,
                            formattedAmount = displayInfo.formattedAmount,
                            formattedFee = formatCryptoAmount(transaction.fee),
                            formattedTime = displayInfo.formattedTime,
                            usdValue = usdValue,
                            formattedUsd = formattedUsd,
                            isLoading = false
                        )
                    }
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            error = result.message,
                            isLoading = false
                        )
                    }
                    _effects.emit(TransactionDetailEffect.ShowError(result.message))
                }

                Result.Loading -> {}
            }
        }
    }

    private fun calculateUSDValue(transaction: TransactionDetail): Double {
        // For now, return 0 or a mock value
        val amount = transaction.amount.toDoubleOrNull() ?: 0.0

        // TODO: replace with actual price fetching
        val mockPrices = mapOf(
            "bitcoin" to 65000.0,
            "ethereum" to 3500.0,
            "solana" to 150.0,
            "usd-coin" to 1.0
        )

        val priceKey = when (transaction.coinType) {
            CoinType.BITCOIN -> "bitcoin"
            CoinType.ETHEREUM -> "ethereum"
            CoinType.SOLANA -> "solana"
            CoinType.USDC -> "usd-coin"
            else -> transaction.tokenSymbol?.lowercase() ?: ""
        }

        val price = mockPrices[priceKey] ?: 0.0
        return amount * price
    }

    private fun createDummyBitcoinTx(transaction: TransactionDetail): BitcoinTransaction {
        return BitcoinTransaction(
            id = transaction.id,
            walletId = transaction.walletId,
            fromAddress = transaction.fromAddress,
            toAddress = transaction.toAddress,
            status = transaction.status,
            timestamp = transaction.timestamp,
            note = transaction.memo,
            feeLevel = FeeLevel.NORMAL,
            amountSatoshis = 0,
            amountBtc = transaction.amount,
            feeSatoshis = 0,
            feeBtc = transaction.fee,
            feePerByte = transaction.feePerByte ?: 0.0,
            estimatedSize = transaction.estimatedSize?.toLong() ?: 0,
            signedHex = null,
            txHash = transaction.hash,
            network = when {
                transaction.network.contains("Testnet") -> BitcoinNetwork.Testnet
                else -> BitcoinNetwork.Mainnet
            },
            isIncoming = transaction.isIncoming
        )
    }

    private fun createDummySolanaTx(transaction: TransactionDetail): SolanaTransaction {
        return SolanaTransaction(
            id = transaction.id,
            walletId = transaction.walletId,
            fromAddress = transaction.fromAddress,
            toAddress = transaction.toAddress,
            status = transaction.status,
            timestamp = transaction.timestamp,
            note = transaction.memo,
            feeLevel = FeeLevel.NORMAL,
            amountLamports = 0,
            amountSol = transaction.amount,
            feeLamports = 0,
            feeSol = transaction.fee,
            signature = transaction.hash,
            network = when {
                transaction.network.contains("Devnet") -> SolanaNetwork.Devnet
                else -> SolanaNetwork.Mainnet
            },
            isIncoming = transaction.isIncoming,
            tokenMint = transaction.tokenContract,
            tokenSymbol = transaction.tokenSymbol,
            tokenDecimals = transaction.tokenDecimals,
            slot = transaction.slot,
            blockTime = transaction.blockHeight
        )
    }

    private fun createDummyEVMTx(transaction: TransactionDetail, coinType: CoinType): Any {
        if (transaction.tokenSymbol != null) {
            return TokenTransaction(
                id = transaction.id,
                walletId = transaction.walletId,
                fromAddress = transaction.fromAddress,
                toAddress = transaction.toAddress,
                status = transaction.status,
                timestamp = transaction.timestamp,
                note = transaction.memo,
                feeLevel = FeeLevel.NORMAL,
                amountWei = "0",
                amountDecimal = transaction.amount,
                gasPriceWei = "0",
                gasPriceGwei = transaction.gasPrice ?: "0",
                gasLimit = transaction.gasUsed ?: 0,
                feeWei = "0",
                feeEth = transaction.fee,
                nonce = transaction.nonce ?: 0,
                chainId = transaction.chainId?.toLong() ?: 0,
                signedHex = null,
                txHash = transaction.hash,
                network = when {
                    transaction.network.contains("Sepolia") -> EthereumNetwork.Sepolia.chainId
                    else -> EthereumNetwork.Mainnet.chainId
                },
                isIncoming = transaction.isIncoming,
                tokenContract = transaction.tokenContract ?: "",
                tokenSymbol = transaction.tokenSymbol ?: "",
                tokenDecimals = transaction.tokenDecimals ?: 18,
                data = "",
                tokenExternalId = ""
            )
        } else {
            return NativeETHTransaction(
                id = transaction.id,
                walletId = transaction.walletId,
                fromAddress = transaction.fromAddress,
                toAddress = transaction.toAddress,
                status = transaction.status,
                timestamp = transaction.timestamp,
                note = transaction.memo,
                feeLevel = FeeLevel.NORMAL,
                amountWei = "0",
                amountEth = transaction.amount,
                gasPriceWei = "0",
                gasPriceGwei = transaction.gasPrice ?: "0",
                gasLimit = transaction.gasUsed ?: 0,
                feeWei = "0",
                feeEth = transaction.fee,
                nonce = transaction.nonce ?: 0,
                chainId = transaction.chainId?.toLong() ?: 0,
                signedHex = null,
                txHash = transaction.hash,
                network = when {
                    transaction.network.contains("Sepolia") -> EthereumNetwork.Sepolia.chainId
                    else -> EthereumNetwork.Mainnet.chainId
                },
                isIncoming = transaction.isIncoming,
                data = "",
                tokenExternalId = null
            )
        }
    }

    fun refresh() {
        val currentState = _state.value
        currentState.transaction?.let { tx ->
            loadTransactionDetail(tx.walletId, tx.id, tx.coinType)
        }
    }

    fun copyToClipboard(text: String, label: String) {
        viewModelScope.launch {
            _effects.emit(TransactionDetailEffect.CopyToClipboard(text, label))
        }
    }

    fun shareTransaction() {
        viewModelScope.launch {
            _effects.emit(TransactionDetailEffect.ShareTransaction)
        }
    }

    fun openInExplorer() {
        viewModelScope.launch {
            _state.value.transaction?.let { tx ->
                val network = when (tx.coinType) {
                    CoinType.BITCOIN -> when {
                        tx.network.contains("Testnet") -> "testnet"
                        else -> "mainnet"
                    }
                    CoinType.ETHEREUM, CoinType.USDC -> when {
                        tx.network.contains("Sepolia") -> "sepolia"
                        else -> "mainnet"
                    }
                    CoinType.SOLANA -> when {
                        tx.network.contains("Devnet") -> "devnet"
                        else -> "mainnet"
                    }
                }

                val url = ExplorerUrlHelper.getExplorerUrl(tx.hash, tx.coinType, network)
                _effects.emit(TransactionDetailEffect.OpenExplorer(url))
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        fun formatCryptoAmount(amount: String): String {
            return try {
                val amountDecimal = amount.toBigDecimal()
                if (amountDecimal.compareTo(BigDecimal.ZERO) == 0) {
                    return "0"
                }

                when {
                    amountDecimal < BigDecimal("0.000001") -> {
                        amountDecimal.setScale(8, RoundingMode.HALF_UP)
                            .stripTrailingZeros()
                            .toPlainString()
                    }
                    amountDecimal < BigDecimal("0.001") -> {
                        amountDecimal.setScale(6, RoundingMode.HALF_UP)
                            .stripTrailingZeros()
                            .toPlainString()
                    }
                    amountDecimal < BigDecimal("1") -> {
                        amountDecimal.setScale(4, RoundingMode.HALF_UP)
                            .stripTrailingZeros()
                            .toPlainString()
                    }
                    else -> {
                        amountDecimal.setScale(8, RoundingMode.HALF_UP)
                            .stripTrailingZeros()
                            .toPlainString()
                    }
                }
            } catch (e: Exception) {
                amount
            }
        }
    }
}