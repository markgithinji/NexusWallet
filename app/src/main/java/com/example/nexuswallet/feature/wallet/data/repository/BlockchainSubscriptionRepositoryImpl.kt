package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.BlockchainSubscriptionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class BlockchainSubscriptionRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BlockchainSubscriptionRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    
    // Shared flow for all address change events
    private val _addressChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)
    
    // Manage active web sockets: Network -> WebSocket
    private val activeWebSockets = Collections.synchronizedMap(mutableMapOf<Network, WebSocket>())
    
    // Track subscribed addresses: Network -> Set<Address>
    private val subscribedAddresses = Collections.synchronizedMap(mutableMapOf<Network, MutableSet<String>>())
    
    // Reconnection tracking
    private val reconnectAttempts = Collections.synchronizedMap(mutableMapOf<Network, Int>())
    private val connectionMutex = Mutex()

    override fun subscribeToAddressChanges(address: String, network: Network): Flow<String> {
        scope.launch {
            connectionMutex.withLock {
                val addresses = subscribedAddresses.getOrPut(network) { mutableSetOf() }
                if (addresses.add(address)) {
                    ensureWebSocketConnected(network)
                    sendSubscription(network, address)
                }
            }
        }
        return _addressChanges
    }

    override fun unsubscribeFromAddress(address: String, network: Network) {
        scope.launch {
            connectionMutex.withLock {
                subscribedAddresses[network]?.remove(address)
                // We could send an unsubscribe message here if the protocol supports it
                // For Mempool and Solana, we usually just keep the socket open if other addresses are active
            }
        }
    }

    override fun clearAllSubscriptions() {
        scope.launch {
            connectionMutex.withLock {
                subscribedAddresses.clear()
                activeWebSockets.values.forEach { it.close(1000, "Clearing all subscriptions") }
                activeWebSockets.clear()
                reconnectAttempts.clear()
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
                // 1. Subscribe to newHeads for native balance changes
                val headsMessage = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "eth_subscribe")
                    put("params", buildJsonArray { add("newHeads") })
                }.toString()
                webSocket.send(headsMessage)

                // 2. Subscribe to logs for token transfers TO this address
                val logsToMessage = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 2)
                    put("method", "eth_subscribe")
                    put("params", buildJsonArray {
                        add("logs")
                        add(buildJsonObject {
                            // Topic for Transfer(address,address,uint256) is 0xddf252ad...
                            // second parameter (index 2 in topics) is 'to' address
                            put("topics", buildJsonArray {
                                add("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
                                add(null) // from any
                                add("0x000000000000000000000000${address.removePrefix("0x")}")
                            })
                        })
                    })
                }.toString()
                webSocket.send(logsToMessage)
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
                reconnectAttempts[network] = attempts + 1
                val delayMs = getReconnectDelay(attempts + 1)
                
                scope.launch {
                    delay(delayMs)
                    connectionMutex.withLock {
                        ensureWebSocketConnected(network)
                    }
                }
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
                    // Solana accountNotification
                    if (jsonElement is JsonObject && (jsonElement["method"]?.jsonPrimitive?.content == "accountNotification")) {
                        // Extract address from subscription ID or just notify change
                        // Since we only subscribe to user addresses, any notification here means one of them changed
                        // For simplicity, we emit a signal that forces a refresh of all Solana addresses
                        _addressChanges.emit("SOLANA_SIGNAL")
                    }
                }
                is EthereumNetwork -> {
                    // Ethereum newHeads signal
                    if (jsonElement is JsonObject && (jsonElement["method"]?.jsonPrimitive?.content == "eth_subscription")) {
                        _addressChanges.emit("ETHEREUM_SIGNAL")
                    }
                }
                is BitcoinNetwork -> {
                    // Mempool.space address-transactions
                    val jsonObject = jsonElement.jsonObject
                    if (jsonObject.containsKey("address-transactions") || jsonObject.containsKey("address-utxo")) {
                        // Mempool usually returns the address in the message
                        // If not, we just signal
                        _addressChanges.emit("BITCOIN_SIGNAL")
                    }
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error parsing WebSocket message from ${network.name}: ${e.message}")
        }
    }

    private fun getReconnectDelay(attempt: Int): Long {
        return min(BASE_RECONNECT_DELAY_MS * (1 shl (attempt - 1)), MAX_RECONNECT_DELAY_MS)
    }

    companion object {
        private const val TAG = "BlockchainSubRepo"
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val BASE_RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 60000L
    }
}
