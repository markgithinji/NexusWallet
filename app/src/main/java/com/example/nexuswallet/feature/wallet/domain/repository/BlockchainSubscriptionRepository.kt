package com.example.nexuswallet.feature.wallet.domain.repository

import com.example.nexuswallet.feature.wallet.domain.model.Network
import kotlinx.coroutines.flow.Flow

/**
 * Repository responsible for managing long-lived blockchain subscriptions (WebSockets).
 * Emits address-related events when transactions or balance changes are detected.
 */
interface BlockchainSubscriptionRepository {
    
    /**
     * Subscribes to changes for a specific address on a given network.
     * Emits the address itself when a change is detected.
     */
    fun subscribeToAddressChanges(address: String, network: Network): Flow<String>

    /**
     * Unsubscribes from a specific address on a given network.
     */
    fun unsubscribeFromAddress(address: String, network: Network)

    /**
     * Clears all active subscriptions and closes connections.
     */
    fun clearAllSubscriptions()
}
