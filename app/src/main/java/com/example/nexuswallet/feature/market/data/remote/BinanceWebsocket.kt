package com.example.nexuswallet.feature.market.data.remote


import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min

@Serializable
data class BinanceTicker(
    val symbol: String,        // "BTCUSDT"
    val price: String,         // "45231.45" (current price - "c" field)
    val priceChange: String,   // "123.45" (24h price change - "p" field)
    val priceChangePercent: String // "2.45" (24h change percentage - "P" field)
)

@Serializable
data class TokenPriceUpdate(
    val tokenId: String,
    val price: Double,
    val priceChange24h: Double,
    val priceChangePercentage24h: Double
)

class BinanceWebSocket : WebSocketListener() {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

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

    // Split into batches of 20 symbols per connection (Binance URL limit)
    private val symbolBatches = symbolMapping.keys.chunked(20)
    private val webSockets = mutableListOf<WebSocket>()
    private val batchConnectionStates = MutableStateFlow(List(symbolBatches.size) { false })

    // Flow for full updates (price + percentage)
    private val _fullUpdates = MutableSharedFlow<Map<String, TokenPriceUpdate>>(
        replay = 1,
        extraBufferCapacity = 50
    )
    val fullUpdates: SharedFlow<Map<String, TokenPriceUpdate>> = _fullUpdates

    // Keep the simple price updates for backward compatibility
    private val _priceUpdates = MutableSharedFlow<Map<String, Double>>(
        replay = 1,
        extraBufferCapacity = 50
    )
    val priceUpdates: SharedFlow<Map<String, Double>> = _priceUpdates

    // Overall connection state (true if at least one batch is connected)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    enum class ConnectionState {
        CONNECTED, DISCONNECTED, CONNECTING, ERROR
    }

    fun connect() {
        Log.d("BinanceWS", "Attempting to connect ${symbolBatches.size} batches...")
        _connectionState.value = ConnectionState.CONNECTING
        webSockets.clear()

        // Connect each batch
        symbolBatches.forEachIndexed { index, batch ->
            connectBatch(batch, index)
        }

        // Start connection monitor
        startConnectionMonitor()
    }

    private fun connectBatch(batch: List<String>, batchIndex: Int) {
        try {
            val streams = batch.joinToString("/") { "${it.lowercase()}@ticker" }
            val request = Request.Builder()
                .url("wss://stream.binance.com:9443/ws/$streams")
                .build()

            val webSocket = client.newWebSocket(request, createBatchListener(batchIndex))
            webSockets.add(webSocket)

            Log.d("BinanceWS", "Batch $batchIndex connecting with ${batch.size} symbols")
        } catch (e: Exception) {
            Log.e("BinanceWS", "Failed to connect batch $batchIndex: ${e.message}")
        }
    }

    private fun createBatchListener(batchIndex: Int) = object : WebSocketListener() {
        private var reconnectAttempts = 0
        private val maxReconnectAttempts = 5

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d("BinanceWS", "Batch $batchIndex connected successfully")
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

                // Log periodically (every ~100 messages)
                if (System.currentTimeMillis() % 10000 < 100) {
                    Log.d("🔴 LIVE DATA", "Batch $batchIndex - $symbol: $$price (${priceChangePercent}%)")
                }

                val tokenId = symbolMapping[symbol]
                if (tokenId != null) {
                    val update = TokenPriceUpdate(
                        tokenId = tokenId,
                        price = price,
                        priceChange24h = priceChange,
                        priceChangePercentage24h = priceChangePercent
                    )

                    CoroutineScope(Dispatchers.IO).launch {
                        _fullUpdates.emit(mapOf(tokenId to update))
                        _priceUpdates.emit(mapOf(tokenId to price))
                    }
                }
            } catch (e: Exception) {
                Log.e("BinanceWS", "Error parsing message in batch $batchIndex: ${e.message}")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e("BinanceWS", "Batch $batchIndex failed: ${t.message}")
            updateBatchState(batchIndex, false)

            // Attempt to reconnect this batch
            if (reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++
                val delayMs = min(3000 * reconnectAttempts, 30000).toLong()

                CoroutineScope(Dispatchers.IO).launch {
                    delay(delayMs)
                    Log.d("BinanceWS", "Attempting to reconnect batch $batchIndex (attempt $reconnectAttempts)")
                    connectBatch(symbolBatches[batchIndex], batchIndex)
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("BinanceWS", "Batch $batchIndex closed: $reason")
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
            _connectionState.value = if (anyConnected)
                ConnectionState.CONNECTED
            else
                ConnectionState.DISCONNECTED
        }
    }

    private fun startConnectionMonitor() {
        CoroutineScope(Dispatchers.IO).launch {
            batchConnectionStates.collect { states ->
                val connectedCount = states.count { it }
                val totalCount = states.size
                if (connectedCount > 0) {
                    Log.d("BinanceWS", "Connection status: $connectedCount/$totalCount batches connected")
                }
            }
        }
    }

    fun disconnect() {
        webSockets.forEach { it.close(1000, "User disconnected") }
        webSockets.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.d("BinanceWS", "All connections closed")
    }

    fun getConnectedBatchesCount(): Int = batchConnectionStates.value.count { it }

    companion object {
        @Volatile
        private var instance: BinanceWebSocket? = null

        fun getInstance(): BinanceWebSocket {
            return instance ?: synchronized(this) {
                instance ?: BinanceWebSocket().also { instance = it }
            }
        }
    }
}