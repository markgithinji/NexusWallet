package com.example.nexuswallet.feature.core.service

import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.logging.Logger
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class BlockchainSubscriptionService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val wsClient = okHttpClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _addressChanges = MutableSharedFlow<Network>(extraBufferCapacity = 64)
    val addressChanges: Flow<Network> = _addressChanges

    private val activeWebSockets = Collections.synchronizedMap(mutableMapOf<Network, WebSocket>())
    private val subscribedAddresses =
        Collections.synchronizedMap(mutableMapOf<Network, MutableSet<String>>())
    private val reconnectAttempts = Collections.synchronizedMap(mutableMapOf<Network, Int>())
    private val deadNetworks = Collections.synchronizedSet(mutableSetOf<Network>())
    private val connectionMutex = Mutex()
    private var nextId = 1

    /**
     * Registers an address for tracking. Returns Unit to avoid unused Flow warnings.
     * ViewModels should collect from the global [addressChanges] flow instead.
     */
    fun subscribeToAddressChanges(address: String, network: Network) {
        if (deadNetworks.contains(network)) return

        scope.launch {
            connectionMutex.withLock {
                val addresses = subscribedAddresses.getOrPut(network) { mutableSetOf() }
                val isNewAddress = addresses.add(address)
                ensureWebSocketConnected(network)
                if (isNewAddress) {
                    sendSubscription(network, address)
                }
            }
        }
    }

    fun unsubscribeFromAddress(address: String, network: Network) {
        scope.launch {
            connectionMutex.withLock {
                subscribedAddresses[network]?.remove(address)
            }
        }
    }

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

        val request = Request.Builder()
            .url(url)
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            .build()

        val listener = createWebSocketListener(network)
        val webSocket = wsClient.newWebSocket(request, listener)
        activeWebSockets[network] = webSocket
    }

    private fun getWebSocketUrl(network: Network): String? {
        return when (network) {
            is SolanaNetwork -> {
                val apiKey = BuildConfig.HELIUS_API_KEY
                if (network is SolanaNetwork.Mainnet) "wss://mainnet.helius-rpc.com/?api-key=$apiKey"
                else "wss://devnet.helius-rpc.com/?api-key=$apiKey"
            }

            is EthereumNetwork -> {
                val apiKey = BuildConfig.ALCHEMY_API_KEY
                if (network is EthereumNetwork.Mainnet) "wss://eth-mainnet.g.alchemy.com/v2/$apiKey"
                else "wss://eth-sepolia.g.alchemy.com/v2/$apiKey"
            }

            is BitcoinNetwork -> {
                if (network is BitcoinNetwork.Mainnet) "wss://mempool.space/api/v1/ws"
                else "wss://mempool.space/testnet/api/v1/ws"
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
                // 1. SURGICAL MINED TRANSACTIONS (Native ETH & Outgoing)
                val minedMsg = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", nextId++)
                    put("method", "eth_subscribe")
                    put("params", buildJsonArray {
                        add("alchemy_minedTransactions")
                        add(buildJsonObject {
                            put("addresses", buildJsonArray {
                                add(buildJsonObject { put("to", address) })
                                add(buildJsonObject { put("from", address) })
                            })
                        })
                    })
                }.toString()
                webSocket.send(minedMsg)

                // 2. SURGICAL LOGS (Incoming Tokens)
                val logsMsg = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", nextId++)
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
                webSocket.send(logsMsg)
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

            subscribedAddresses[network]?.forEach { address -> sendSubscription(network, address) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { handleMessage(network, text) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            activeWebSockets.remove(network)
            val attempts = reconnectAttempts.getOrDefault(network, 0)
            if (attempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts[network] = attempts + 1
                scope.launch {
                    delay(getReconnectDelay(attempts + 1))
                    connectionMutex.withLock {
                        if (!deadNetworks.contains(network)) ensureWebSocketConnected(
                            network
                        )
                    }
                }
            } else {
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
            if (jsonElement is JsonObject) {
                val method = jsonElement["method"]?.jsonPrimitive?.content
                if (method == "accountNotification" || method == "eth_subscription") {
                    _addressChanges.emit(network)
                } else if (network is BitcoinNetwork) {
                    if (jsonElement.containsKey("address-transactions") || jsonElement.containsKey("address-utxo")) {
                        _addressChanges.emit(network)
                    }
                }
            }
        } catch (_: Exception) {
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
