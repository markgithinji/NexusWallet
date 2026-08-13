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
import com.example.nexuswallet.feature.logging.Logger
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class BlockchainSubscriptionService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val TAG = "BlockchainSubService"

    /**
     * Custom DNS that prefers IPv4 to avoid ECONNREFUSED issues with IPv6 in some environments.
     */
    private val ipv4Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val addresses = Dns.SYSTEM.lookup(hostname)
                val filtered = addresses.filter { it is java.net.Inet4Address }
                if (filtered.isNotEmpty()) filtered else addresses
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Dedicated client for WebSockets with snappier timeouts and forced IPv4.
     */
    private val wsClient = okHttpClient.newBuilder()
        .dns(ipv4Dns)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    /**
     * Scope for managing background WebSocket tasks.
     * Uses SupervisorJob so that a failure in one network subscription doesn't kill the others.
     */
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // Shared flow for all address change events. Emits the network that triggered the signal.
    private val _addressChanges = MutableSharedFlow<Network>(extraBufferCapacity = 64)
    val addressChanges: Flow<Network> = _addressChanges

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
    private var nextId = 1

    /**
     * Subscribes to changes for a specific address on a given network.
     * Emits the address itself when a change is detected.
     */
    fun subscribeToAddressChanges(address: String, network: Network): Flow<Network> {
        // DISABLED: Bitcoin WSS is currently disabled to prevent connection issues and API rate limiting.
        // We rely on global refreshes triggered by ETH/SOL activity to update BTC balances.
        if (network is BitcoinNetwork) return _addressChanges

        // If the circuit breaker is blown for this network, don't even try to connect.
        if (deadNetworks.contains(network)) {
            logger.d(TAG, "Circuit breaker is BLOWN for ${network.name}. Skipping subscription for $address")
            return _addressChanges
        }

        scope.launch {
            connectionMutex.withLock {
                val addresses = subscribedAddresses.getOrPut(network) { mutableSetOf() }
                val isNewAddress = addresses.add(address)
                
                // ALWAYS ensure the socket is connected when someone asks for a subscription.
                ensureWebSocketConnected(network)
                
                // Only send the subscription message if the address is new to this socket's state.
                if (isNewAddress) {
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
     * Clears the circuit breaker and active socket for a network, forcing a fresh connection.
     */
    fun reconnectNetwork(network: Network) {
        scope.launch {
            connectionMutex.withLock {
                logger.d(TAG, "Force reconnecting network: ${network.name}")
                deadNetworks.remove(network)
                reconnectAttempts[network] = 0
                activeWebSockets[network]?.close(1000, "Forced reconnection")
                activeWebSockets.remove(network)
                ensureWebSocketConnected(network)

                // Resubscribe all addresses for this network
                subscribedAddresses[network]?.forEach { address ->
                    sendSubscription(network, address)
                }
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
        logger.d(TAG, "Connecting WebSocket for ${network.name} to $url")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val listener = createWebSocketListener(network)
        val webSocket = wsClient.newWebSocket(request, listener)
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
                    put("id", nextId++)
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
                // Subscribe to INCOMING tokens (Transfer to this address)
                val incomingTokenLogsMessage = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", nextId++)
                    put("method", "eth_subscribe")
                    put("params", buildJsonArray {
                        add("logs")
                        add(buildJsonObject {
                            put("topics", buildJsonArray {
                                add("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef") // Transfer event
                                add(null)
                                add("0x000000000000000000000000${address.removePrefix("0x")}") // to: address
                            })
                        })
                    })
                }.toString()
                webSocket.send(incomingTokenLogsMessage)

                // Subscribe to OUTGOING tokens (Transfer from this address)
                val outgoingTokenLogsMessage = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", nextId++)
                    put("method", "eth_subscribe")
                    put("params", buildJsonArray {
                        add("logs")
                        add(buildJsonObject {
                            put("topics", buildJsonArray {
                                add("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef") // Transfer event
                                add("0x000000000000000000000000${address.removePrefix("0x")}") // from: address
                            })
                        })
                    })
                }.toString()
                webSocket.send(outgoingTokenLogsMessage)
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
            logger.d(TAG, "WebSocket OPENED for ${network.name}")
            reconnectAttempts[network] = 0

            // If it's Ethereum, subscribe to newHeads to catch native ETH transfers (which don't emit logs)
            if (network is EthereumNetwork) {
                val newHeadsMsg = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", nextId++)
                    put("method", "eth_subscribe")
                    put("params", buildJsonArray { add("newHeads") })
                }.toString()
                webSocket.send(newHeadsMsg)
            }

            // If it's Bitcoin, send the 'want' message to initialize the stream
            if (network is BitcoinNetwork) {
                val initMsg = buildJsonObject {
                    put("action", "want")
                    put("data", buildJsonArray {
                        add("blocks")
                        add("mempool-blocks")
                        add("stats")
                    })
                }.toString()
                webSocket.send(initMsg)
            }

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
            logger.e(TAG, "WebSocket FAILURE for ${network.name}: ${t.message}")
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
            logger.d(TAG, "WebSocket CLOSED for ${network.name}")
            activeWebSockets.remove(network)
        }
    }

    private suspend fun handleMessage(network: Network, text: String) {
        try {
            logger.d(TAG, "RAW MSG from ${network.name}: $text")
            val jsonElement = json.parseToJsonElement(text)

            when (network) {
                is SolanaNetwork -> {
                    if (jsonElement is JsonObject && (jsonElement["method"]?.jsonPrimitive?.content == "accountNotification")) {
                        _addressChanges.emit(network)
                    }
                }

                is EthereumNetwork -> {
                    if (jsonElement is JsonObject && jsonElement["method"]?.jsonPrimitive?.content == "eth_subscription") {
                        _addressChanges.emit(network)
                    }
                }

                is BitcoinNetwork -> {
                    if (jsonElement is JsonObject && (jsonElement.containsKey("address-transactions") || jsonElement.containsKey(
                            "address-utxo"
                        ))
                    ) {
                        _addressChanges.emit(network)
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
