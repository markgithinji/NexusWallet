package com.example.nexuswallet.feature.market.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class BinanceWebSocket @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    ioDispatcher: CoroutineDispatcher
) {
    // Create scope with injected dispatcher
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var connectionMonitorJob: Job? = null

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

    // Split into batches
    private val symbolBatches = symbolMapping.keys.chunked(SYMBOLS_PER_BATCH)
    private val webSockets = mutableListOf<WebSocket>()
    private val batchConnectionStates = MutableStateFlow(List(symbolBatches.size) { false })

    // Flows for updates
    private val _fullUpdates = MutableSharedFlow<Map<String, TokenPriceUpdate>>(
        replay = 1,
        extraBufferCapacity = 50
    )
    val fullUpdates: SharedFlow<Map<String, TokenPriceUpdate>> = _fullUpdates

    private val _priceUpdates = MutableSharedFlow<Map<String, Double>>(
        replay = 1,
        extraBufferCapacity = 50
    )
    val priceUpdates: SharedFlow<Map<String, Double>> = _priceUpdates

    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun connect() {
        Log.d(TAG, "Attempting to connect ${symbolBatches.size} batches...")
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

            Log.d(TAG, "Batch $batchIndex connecting with ${batch.size} symbols")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect batch $batchIndex: ${e.message}")
        }
    }

    private fun createBatchListener(batchIndex: Int) = object : WebSocketListener() {
        private var reconnectAttempts = 0

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Batch $batchIndex connected successfully")
            updateBatchState(batchIndex, true)
            reconnectAttempts = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val jsonObject = json.parseToJsonElement(text).jsonObject

                val symbol = jsonObject["s"]?.jsonPrimitive?.content ?: return
                val price = jsonObject["c"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return
                val priceChange = jsonObject["p"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val priceChangePercent = jsonObject["P"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0

                // Log periodically
                if (System.currentTimeMillis() % LOG_INTERVAL_MS < 100) {
                    Log.d(TAG, "Batch $batchIndex - $symbol: $$price (${priceChangePercent}%)")
                }

                val tokenId = symbolMapping[symbol]
                if (tokenId != null) {
                    val update = TokenPriceUpdate(
                        tokenId = tokenId,
                        price = price,
                        priceChange24h = priceChange,
                        priceChangePercentage24h = priceChangePercent
                    )

                    scope.launch {
                        _fullUpdates.emit(mapOf(tokenId to update))
                        _priceUpdates.emit(mapOf(tokenId to price))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message in batch $batchIndex: ${e.message}")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Batch $batchIndex failed: ${t.message}")
            updateBatchState(batchIndex, false)

            // Exponential backoff reconnection
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++
                val delayMs = getReconnectDelay(reconnectAttempts)

                scope.launch {
                    delay(delayMs)
                    Log.d(TAG, "Attempting to reconnect batch $batchIndex (attempt $reconnectAttempts)")
                    connectBatch(symbolBatches[batchIndex], batchIndex)
                }
            } else {
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Batch $batchIndex closed: $reason")
            updateBatchState(batchIndex, false)
        }
    }

    private fun updateBatchState(batchIndex: Int, isConnected: Boolean) {
        val currentStates = batchConnectionStates.value.toMutableList()
        if (batchIndex < currentStates.size) {
            currentStates[batchIndex] = isConnected
            batchConnectionStates.value = currentStates

            val anyConnected = currentStates.any { it }
            _connectionState.value = if (anyConnected)
                ConnectionState.CONNECTED
            else
                ConnectionState.DISCONNECTED
        }
    }

    private fun startConnectionMonitor() {
        connectionMonitorJob = scope.launch {
            batchConnectionStates.collect { states ->
                val connectedCount = states.count { it }
                val totalCount = states.size
                if (connectedCount > 0) {
                    Log.d(TAG, "Connection status: $connectedCount/$totalCount batches connected")
                }
            }
        }
    }

    fun disconnect() {
        connectionMonitorJob?.cancel()
        webSockets.forEach { it.close(1000, "User disconnected") }
        webSockets.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.d(TAG, "All connections closed")
    }

    fun cleanup() {
        disconnect()
        scope.cancel()
    }

    private fun getReconnectDelay(attempt: Int): Long {
        // Exponential backoff: 3s, 6s, 12s, 24s, 30s (capped)
        return min(
            BASE_RECONNECT_DELAY_MS * (1 shl (attempt - 1)),
            MAX_RECONNECT_DELAY_MS
        )
    }

    companion object {
        private const val TAG = "BinanceWS"
        private const val BINANCE_WS_URL = "wss://stream.binance.com:9443/ws/"
        private const val SYMBOLS_PER_BATCH = 20
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BASE_RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val LOG_INTERVAL_MS = 10000L
    }
}