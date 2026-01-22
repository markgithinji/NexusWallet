package com.example.nexuswallet.data.remote


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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class CoinCapWebSocket : WebSocketListener() {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private var webSocket: WebSocket? = null
    private val json = Json { ignoreUnknownKeys = true }

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    private val _priceUpdates = MutableSharedFlow<Map<String, Double>>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val priceUpdates: SharedFlow<Map<String, Double>> = _priceUpdates

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    enum class ConnectionState {
        CONNECTED, DISCONNECTED, CONNECTING, ERROR
    }

    fun connect(assets: List<String> = listOf("bitcoin", "ethereum", "solana", "cardano")) {
        val assetsParam = assets.joinToString(",")
        val request = Request.Builder()
            .url("wss://ws.coincap.io/prices?assets=$assetsParam")
            .build()

        webSocket = client.newWebSocket(request, this)
        _connectionState.value = ConnectionState.CONNECTING
        reconnectAttempts = 0
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("WebSocket", "Connected to CoinCap")
        _connectionState.value = ConnectionState.CONNECTED
        reconnectAttempts = 0
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val priceMap = json.decodeFromString<Map<String, Double>>(text)

            CoroutineScope(Dispatchers.IO).launch {
                _priceUpdates.emit(priceMap)
            }

        } catch (e: Exception) {
            Log.e("WebSocket", "Error parsing message: ${e.message}")
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e("WebSocket", "Connection failed: ${t.message}")
        _connectionState.value = ConnectionState.ERROR
        reconnectAfterDelay()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d("WebSocket", "Connection closed: $reason")
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun reconnectAfterDelay() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            _connectionState.value = ConnectionState.ERROR
            return
        }

        reconnectAttempts++
        val delayMillis = minOf(3000 * reconnectAttempts, 30000)

        CoroutineScope(Dispatchers.IO).launch {
            delay(delayMillis.toLong())
            if (_connectionState.value != ConnectionState.CONNECTED) {
                connect()
            }
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    companion object {
        @Volatile
        private var instance: CoinCapWebSocket? = null

        fun getInstance(): CoinCapWebSocket {
            return instance ?: synchronized(this) {
                instance ?: CoinCapWebSocket().also { instance = it }
            }
        }
    }
}