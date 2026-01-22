package com.example.nexuswallet.data.remote

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import java.util.concurrent.TimeUnit

class CryptoWebSocket : WebSocketListener() {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val _priceUpdates = MutableSharedFlow<Map<String, String>>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val priceUpdates: SharedFlow<Map<String, String>> = _priceUpdates

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    enum class ConnectionState {
        CONNECTED, DISCONNECTED, CONNECTING, ERROR
    }

    fun connect() {
        Log.d("CryptoWebSocket", "Attempting to connect...")
        _connectionState.value = ConnectionState.CONNECTING

        // Using a public WebSocket that doesn't require API key
        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/btcusdt@ticker/ethusdt@ticker/bnbusdt@ticker")
            .build()

        webSocket = client.newWebSocket(request, this)
        Log.d("CryptoWebSocket", "WebSocket request created")
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("CryptoWebSocket", "CONNECTED to Binance WebSocket")
        _connectionState.value = ConnectionState.CONNECTED
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d("CryptoWebSocket", "Message received")
        try {
            // Parse Binance WebSocket format
            val jsonObject = json.parseToJsonElement(text).jsonObject

            val symbol = jsonObject["s"]?.jsonPrimitive?.content ?: return
            val price = jsonObject["c"]?.jsonPrimitive?.content ?: return

            val priceMap = mapOf(symbol to price)
            Log.d("CryptoWebSocket", "Price update: $symbol = $price")

            CoroutineScope(Dispatchers.IO).launch {
                _priceUpdates.emit(priceMap)
            }

        } catch (e: Exception) {
            Log.e("CryptoWebSocket", "Error parsing: ${e.message}")
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e("CryptoWebSocket", "Connection failed: ${t.message}")
        _connectionState.value = ConnectionState.ERROR

        // Auto-reconnect after 3 seconds
        CoroutineScope(Dispatchers.IO).launch {
            delay(3000)
            Log.d("CryptoWebSocket", "Attempting reconnect...")
            connect()
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d("CryptoWebSocket", "Connection closed: $reason")
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    companion object {
        @Volatile
        private var instance: CryptoWebSocket? = null

        fun getInstance(): CryptoWebSocket {
            return instance ?: synchronized(this) {
                instance ?: CryptoWebSocket().also { instance = it }
            }
        }
    }
}