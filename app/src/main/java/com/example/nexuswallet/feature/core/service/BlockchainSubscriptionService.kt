package com.example.nexuswallet.feature.core.service

import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
class BlockchainSubscriptionService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Scope for managing background WebSocket tasks.
     * Uses SupervisorJob so that a failure in one network subscription doesn't kill the others.
     */
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // Shared flow for all address change events. Emits generic signals like "ETHEREUM_SIGNAL".
    private val _addressChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)

    // Manage active web sockets: Network -> WebSocket. One socket per blockchain network.
    private val activeWebSockets = Collections.synchronizedMap(mutableMapOf<Network, WebSocket>())

    // Track subscribed addresses per network to avoid redundant subscription messages.
    private val subscribedAddresses =
        Collections.synchronizedMap(mutableMapOf<Network, MutableSet<String>>())

    // Circuit Breaker: Tracks failed connection attempts.
    private val reconnectAttempts = Collections.synchronizedMap(mutableMapOf<Network, Int>())

    // Blacklisted networks that have exceeded MAX_RECONNECT_ATTEMPTS.
    private val deadNetworks = Collections.synchronizedSet(mutableSetOf<Network>())
    private val connectionMutex = Mutex()

    /**
     * Subscribes to changes for a specific address on a given network.
     * Emits the address itself when a change is detected.
     */
    fun subscribeToAddressChanges(address: String, network: Network): Flow<String> {
        // If the circuit breaker is blown for this network, don't even try to connect.
        if (deadNetworks.contains(network)) return _addressChanges

        scope.launch {
            connectionMutex.withLock {
                val addresses = subscribedAddresses.getOrPut(network) { mutableSetOf() }
                if (addresses.add(address)) {
                    // Only open the socket and send the subscription message for new addresses.
                    ensureWebSocketConnected(network)
                    sendSubscription(network, address)
                }
            }
        }
        return _addressChanges
    }

    /**
     * Unsubscribes from a specific address on a given network.
     */
    fun unsubscribeFromAddress(address: String, network: Network) {
        scope.launch {
            connectionMutex.withLock {
                subscribedAddresses[network]?.remove(address)
            }
        }
    }

    /**
     * Clears all active subscriptions and closes connections.
     */
    fun clearAllSubscriptions() {
        scope.launch {
            connectionMutex.withLock {
                subscribedAddresses.clear()
                activeWebSockets.values.forEach { it.close(1000, "Clearing all subscriptions") }
                activeWebSockets.clear()
                reconnectAttempts.clear()
                deadNetworks.clear()
            }
        }
    }

    private fun ensureWebSocketConnected(network: Network) {
        if (activeWebSockets.containsKey(network)) return

        val url = getWebSocketUrl(network) ?: return
        val request = Request.Builder().url(url).build()

        val listener = createWebSocketListener(network)
        val webSocket = okHttpClient.newWebSocket(request, listener)
        activeWebSockets[network] = webSocket
    }

    private fun getWebSocketUrl(network: Network): String? {
        return when (network) {
            is SolanaNetwork -> {
                val apiKey = BuildConfig.HELIUS_API_KEY
                if (network is SolanaNetwork.Mainnet)
                    "wss://mainnet.helius-rpc.com/?api-key=$apiKey"
                else
                    "wss://devnet.helius-rpc.com/?api-key=$apiKey"
            }

            is EthereumNetwork -> {
                val apiKey = BuildConfig.ALCHEMY_API_KEY
                if (network is EthereumNetwork.Mainnet)
                    "wss://eth-mainnet.g.alchemy.com/v2/$apiKey"
                else
                    "wss://eth-sepolia.g.alchemy.com/v2/$apiKey"
            }

            is BitcoinNetwork -> {
                if (network is BitcoinNetwork.Mainnet)
                    "wss://mempool.space/api/v1/ws"
                else
                    "wss://mempool.space/testnet/api/v1/ws"
            }
        }
    }

    private fun sendSubscription(network: Network, address: String) {
        val webSocket = activeWebSockets[network] ?: return

        when (network) {
            is SolanaNetwork -> {
                val message = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "accountSubscribe")
                    put("params", buildJsonArray {
                        add(address)
                        add(buildJsonObject {
                            put("encoding", "base64")
                            put("commitment", "confirmed")
                        })
                    })
                }.toString()
                webSocket.send(message)
            }

            is EthereumNetwork -> {
                val logsToMessage = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "eth_subscribe")
                    put("params", buildJsonArray {
                        add("logs")
                        add(buildJsonObject {
                            put("address", address)
                        })
                    })
                }.toString()
                webSocket.send(logsToMessage)

                val tokenLogsMessage = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 2)
                    put("method", "eth_subscribe")
                    put("params", buildJsonArray {
                        add("logs")
                        add(buildJsonObject {
                            put("topics", buildJsonArray {
                                add("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
                                add(null)
                                add("0x000000000000000000000000${address.removePrefix("0x")}")
                            })
                        })
                    })
                }.toString()
                webSocket.send(tokenLogsMessage)
            }

            is BitcoinNetwork -> {
                val message = buildJsonObject {
                    put("track-address", address)
                }.toString()
                webSocket.send(message)
            }
        }
    }

    private fun createWebSocketListener(network: Network) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts[network] = 0

            // Resubscribe all addresses for this network
            subscribedAddresses[network]?.forEach { address ->
                sendSubscription(network, address)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                handleMessage(network, text)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            activeWebSockets.remove(network)

            val attempts = reconnectAttempts.getOrDefault(network, 0)
            if (attempts < MAX_RECONNECT_ATTEMPTS) {
                // Try to reconnect with exponential backoff if below the limit.
                reconnectAttempts[network] = attempts + 1
                val delayMs = getReconnectDelay(attempts + 1)

                scope.launch {
                    delay(delayMs)
                    connectionMutex.withLock {
                        if (!deadNetworks.contains(network)) {
                            ensureWebSocketConnected(network)
                        }
                    }
                }
            } else {
                // Circuit Breaker: Stop trying to connect to this network for this session.
                deadNetworks.add(network)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            activeWebSockets.remove(network)
        }
    }

    private suspend fun handleMessage(network: Network, text: String) {
        try {
            val jsonElement = json.parseToJsonElement(text)

            when (network) {
                is SolanaNetwork -> {
                    if (jsonElement is JsonObject && (jsonElement["method"]?.jsonPrimitive?.content == "accountNotification")) {
                        _addressChanges.emit("SOLANA_SIGNAL")
                    }
                }

                is EthereumNetwork -> {
                    if (jsonElement is JsonObject && (jsonElement["method"]?.jsonPrimitive?.content == "eth_subscription")) {
                        _addressChanges.emit("ETHEREUM_SIGNAL")
                    }
                }

                is BitcoinNetwork -> {
                    if (jsonElement is JsonObject && (jsonElement.containsKey("address-transactions") || jsonElement.containsKey(
                            "address-utxo"
                        ))
                    ) {
                        _addressChanges.emit("BITCOIN_SIGNAL")
                    }
                }
            }
        } catch (_: Exception) {
            // Silently ignore parsing errors for noise reduction
        }
    }

    private fun getReconnectDelay(attempt: Int): Long {
        return min(BASE_RECONNECT_DELAY_MS * (1 shl (attempt - 1)), MAX_RECONNECT_DELAY_MS)
    }

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val BASE_RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 60000L
    }
}
