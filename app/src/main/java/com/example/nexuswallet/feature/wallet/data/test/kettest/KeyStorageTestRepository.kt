package com.example.nexuswallet.feature.wallet.data.test.kettest

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class KeyStorageTestRepository @Inject constructor(
    private val keyManager: KeyManager
) {

    private val TAG = "KeyStorageTest"

    suspend fun testKeyStorage(): String {
        Log.d(TAG, "=== KEY STORAGE TEST ===")

        // Create a test wallet ID
        val testWalletId = "test_wallet_${System.currentTimeMillis()}"
        val testPrivateKey = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"

        Log.d(TAG, "Test Wallet ID: $testWalletId")
        Log.d(TAG, "Test Private Key: ${testPrivateKey.take(10)}...")

        // Test 1: Store key - Use TEST_KEY type
        Log.d(TAG, "Test 1: Storing key...")
        val storeResult = keyManager.storePrivateKey(
            walletId = testWalletId,
            privateKey = testPrivateKey,// Store as TEST_KEY
        )

        if (storeResult.isFailure) {
            return "❌ Store failed: ${storeResult.exceptionOrNull()?.message}"
        }

        Log.d(TAG, "✓ Key stored")

        // Test 2: Check if key exists - Must specify TEST_KEY type!
        Log.d(TAG, "Test 2: Checking if key exists...")
        val hasKey = keyManager.hasPrivateKey(testWalletId)  // ← SPECIFIC TYPE!

        if (!hasKey) {
            return "❌ Key doesn't exist after storing (checked TEST_KEY)"
        }

        Log.d(TAG, "✓ Key exists")

        // Test 3: Retrieve key - Must specify TEST_KEY type!
        Log.d(TAG, "Test 3: Retrieving key...")
        val retrieveResult = keyManager.getPrivateKeyForSigning(testWalletId)  // ← SPECIFIC TYPE!

        if (retrieveResult.isFailure) {
            return "❌ Retrieve failed: ${retrieveResult.exceptionOrNull()?.message}"
        }

        val retrievedKey = retrieveResult.getOrThrow()
        Log.d(TAG, "✓ Key retrieved: ${retrievedKey.take(10)}...")

        // Test 4: Compare keys
        if (retrievedKey != testPrivateKey) {
            return "❌ Keys don't match!\nStored: ${testPrivateKey.take(10)}...\nRetrieved: ${retrievedKey.take(10)}..."
        }

        Log.d(TAG, "✓ Keys match!")

        return "✅ All tests passed!\nWallet: $testWalletId\nKey: ${testPrivateKey.take(10)}..."
    }

    suspend fun testRealWalletKeys(): String {
        Log.d(TAG, "=== REAL WALLET KEY TEST ===")

        val testWalletId = "eth_1770129685786" // Your test3 wallet ID

        Log.d(TAG, "Testing real wallet: $testWalletId")

        // Debug: Check ALL possible key types first
        Log.d(TAG, "Debugging all key types...")
        val debugInfo = debugWalletKeys(testWalletId)
        Log.d(TAG, debugInfo)

        // Try to find ANY key type
        val keyTypes = listOf("ETH_PRIVATE_KEY", "PRIVATE_KEY", "TEST_KEY", "BTC_PRIVATE_KEY")
        var foundType: String? = null

        for (type in keyTypes) {
            val hasKey = keyManager.hasPrivateKey(testWalletId)
            Log.d(TAG, "Checked $type: $hasKey")
            if (hasKey) {
                foundType = type
                break
            }
        }

        if (foundType == null) {
            return "❌ No key found for wallet $testWalletId\nChecked types: ${keyTypes.joinToString()}"
        }

        Log.d(TAG, "Found key with type: $foundType")

        // Try to retrieve with found type
        val keyResult = keyManager.getPrivateKeyForSigning(testWalletId)

        return if (keyResult.isSuccess) {
            val key = keyResult.getOrThrow()
            "✅ Key found!\nType: $foundType\nLength: ${key.length}\nStarts with 0x? ${key.startsWith("0x")}\nFirst 10: ${key.take(10)}..."
        } else {
            "❌ Failed to retrieve: ${keyResult.exceptionOrNull()?.message}"
        }
    }

    suspend fun debugWalletKeys(walletId: String): String {
        val keyTypes = listOf("PRIVATE_KEY", "ETH_PRIVATE_KEY", "BTC_PRIVATE_KEY", "TEST_KEY")
        val results = mutableListOf<String>()

        results.add("=== DEBUG WALLET: $walletId ===")

        keyTypes.forEach { type ->
            val hasKey = keyManager.hasPrivateKey(walletId)
            results.add("$type: ${if (hasKey) "✅" else "❌"}")

            if (hasKey) {
                try {
                    val key = keyManager.getPrivateKeyForSigning(walletId).getOrThrow()
                    results.add("  Key: ${key.take(10)}... (${key.length} chars)")
                    results.add("  Valid ETH? ${keyManager.isValidEthereumPrivateKey(key)}")
                } catch (e: Exception) {
                    results.add("  Error retrieving: ${e.message}")
                }
            }
        }

        // Also check if ANY key exists
        results.add("---")
//        results.add("hasAnyPrivateKey: ${keyManager.hasAnyPrivateKey(walletId)}")

        return results.joinToString("\n")
    }
}