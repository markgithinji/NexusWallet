package com.example.nexuswallet.feature.market.data.remote

import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.market.domain.BinanceWebSocket
import com.example.nexuswallet.feature.market.domain.model.ConnectionState
import com.example.nexuswallet.feature.market.domain.model.TokenPriceUpdate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class BinanceWebSocketImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BinanceWebSocket {

    // Create scope that can be recreated
    private var scope = createScope()
    private var connectionMonitorJob: Job? = null

    // Thread-safe list
    private val webSockets = Collections.synchronizedList(mutableListOf<WebSocket>())

    // Split symbols into batches to avoid WebSocket limits
    private val symbolBatches = symbolMapping.keys.chunked(SYMBOLS_PER_BATCH)
    private val batchConnectionStates = MutableStateFlow(List(symbolBatches.size) { false })

    // Flow for price updates
    private val _priceUpdate = MutableSharedFlow<Map<String, TokenPriceUpdate>>(
        replay = 1,
        extraBufferCapacity = 50
    )
    override val fullUpdates: SharedFlow<Map<String, TokenPriceUpdate>> = _priceUpdate

    // Connection state flow
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private fun createScope() = CoroutineScope(SupervisorJob() + ioDispatcher)

    override fun connect() {
        // Cancel old scope and create new one for fresh start
        scope.cancel()
        scope = createScope()

        _connectionState.value = ConnectionState.CONNECTING
        webSockets.clear()

        symbolBatches.forEachIndexed { index, batch ->
            connectBatch(batch, index)
        }

        startConnectionMonitor()
    }

    private fun connectBatch(batch: List<String>, batchIndex: Int) {
        try {
            val streams = batch.joinToString("/") { "${it.lowercase()}@ticker" }
            val request = Request.Builder()
                .url("${BINANCE_WS_URL}$streams")
                .build()

            val webSocket = okHttpClient.newWebSocket(request, createBatchListener(batchIndex))
            webSockets.add(webSocket)
        } catch (e: Exception) {
            // Update state to reflect connection failure
            updateBatchState(batchIndex, false)
        }
    }

    private fun createBatchListener(batchIndex: Int) = object : WebSocketListener() {
        private var reconnectAttempts = 0

        override fun onOpen(webSocket: WebSocket, response: Response) {
            updateBatchState(batchIndex, true)
            reconnectAttempts = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val jsonObject = json.parseToJsonElement(text).jsonObject

                val symbol = jsonObject["s"]?.jsonPrimitive?.content ?: return
                val price = jsonObject["c"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return
                val priceChange = jsonObject["p"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val priceChangePercent =
                    jsonObject["P"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0

                val tokenId = symbolMapping[symbol]
                if (tokenId != null) {
                    val update = TokenPriceUpdate(
                        tokenId = tokenId,
                        price = price,
                        priceChange24h = priceChange,
                        priceChangePercentage24h = priceChangePercent
                    )

                    scope.launch {
                        _priceUpdate.emit(mapOf(tokenId to update))
                    }
                }
            } catch (e: Exception) {
                // Log parsing errors
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            updateBatchState(batchIndex, false)

            // Exponential backoff reconnection
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++
                val delayMs = getReconnectDelay(reconnectAttempts)

                scope.launch {
                    delay(delayMs)
                    connectBatch(symbolBatches[batchIndex], batchIndex)
                }
            } else {
                // Check if all batches are disconnected
                val allDisconnected = batchConnectionStates.value.all { !it }
                if (allDisconnected) {
                    _connectionState.value = ConnectionState.ERROR
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            updateBatchState(batchIndex, false)
        }
    }

    private fun updateBatchState(batchIndex: Int, isConnected: Boolean) {
        val currentStates = batchConnectionStates.value.toMutableList()
        if (batchIndex < currentStates.size) {
            currentStates[batchIndex] = isConnected
            batchConnectionStates.value = currentStates

            // Update overall connection state
            val anyConnected = currentStates.any { it }
            _connectionState.value = if (anyConnected) {
                ConnectionState.CONNECTED
            } else {
                ConnectionState.DISCONNECTED
            }
        }
    }

    private fun startConnectionMonitor() {
        connectionMonitorJob = scope.launch {
            batchConnectionStates.collect { states ->
                val connectedCount = states.count { it }
                val totalCount = states.size

                // TODO: Can add connection quality monitoring here if needed
                // For example, emit connection stats or trigger reconnection if too many batches are down
                if (connectedCount < totalCount && connectedCount > 0) {
                    // Partial connection ie. some batches are down
                    // TODO: Trigger reconnection for disconnected batches here
                }
            }
        }
    }

    override fun disconnect() {
        connectionMonitorJob?.cancel()
        webSockets.forEach { it.close(1000, "User disconnected") }
        webSockets.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun getReconnectDelay(attempt: Int): Long {
        // Exponential backoff: 3s, 6s, 12s, 24s, 30s (capped)
        return min(
            BASE_RECONNECT_DELAY_MS * (1 shl (attempt - 1)),
            MAX_RECONNECT_DELAY_MS
        )
    }

    companion object {
        private const val BINANCE_WS_URL = "wss://stream.binance.com:9443/ws/"
        private const val SYMBOLS_PER_BATCH = 20
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BASE_RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L

        // Map Binance symbols to token IDs - Top 50+ coins
        private val symbolMapping = mapOf(
            // Top 10
            "BTCUSDT" to "bitcoin",
            "ETHUSDT" to "ethereum",
            "BNBUSDT" to "binancecoin",
            "SOLUSDT" to "solana",
            "XRPUSDT" to "ripple",
            "ADAUSDT" to "cardano",
            "DOGEUSDT" to "dogecoin",
            "DOTUSDT" to "polkadot",
            "MATICUSDT" to "matic-network",
            "SHIBUSDT" to "shiba-inu",

            // Layer 1s
            "AVAXUSDT" to "avalanche-2",
            "TRXUSDT" to "tron",
            "LINKUSDT" to "chainlink",
            "WBTCUSDT" to "wrapped-bitcoin",
            "LEOUSDT" to "leo-token",
            "TONUSDT" to "the-open-network",
            "DAIUSDT" to "dai",
            "XLMUSDT" to "stellar",
            "ATOMUSDT" to "cosmos",
            "ICPUSDT" to "internet-computer",

            // DeFi & L2s
            "ETCUSDT" to "ethereum-classic",
            "FILUSDT" to "filecoin",
            "APTUSDT" to "aptos",
            "IMXUSDT" to "immutable-x",
            "NEARUSDT" to "near",
            "OPUSDT" to "optimism",
            "ARBUSDT" to "arbitrum",
            "LDOUSDT" to "lido-dao",
            "AAVEUSDT" to "aave",
            "MKRUSDT" to "maker",

            // Meme coins
            "PEPEUSDT" to "pepe",
            "WIFUSDT" to "dogwifcoin",
            "FLOKIUSDT" to "floki",
            "BONKUSDT" to "bonk",

            // Exchange tokens
            "CROUSDT" to "crypto-com-chain",
            "OKBUSDT" to "okb",
            "GTUSDT" to "gatechain-token",

            // More popular coins
            "VETUSDT" to "vechain",
            "QNTUSDT" to "quant",
            "EGLDUSDT" to "elrond",
            "THETAUSDT" to "theta-token",
            "FTMUSDT" to "fantom",
            "SANDUSDT" to "the-sandbox",
            "MANAUSDT" to "decentraland",
            "AXSUSDT" to "axie-infinity",
            "KLAYUSDT" to "klaytn",
            "HNTUSDT" to "helium",
            "ZECUSDT" to "zcash",
            "DASHUSDT" to "dash"
        )
    }
}