package com.example.nexuswallet.feature.core.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of SLIP-0010: Deterministic Derivation of Ed25519 Keys from Seed.
 * This is the industry standard for deriving Solana (and other Ed25519-based) HD keys.
 */
object Slip10 {
    private const val HMAC_SHA512 = "HmacSHA512"
    private val ED25519_SEED = "ed25519 seed".toByteArray(Charsets.UTF_8)

    /**
     * Derives a child private key from a master seed and a BIP-44 path.
     * Note: Ed25519 only supports hardened derivation (indices >= 2^31).
     *
     * @param seed The master seed (usually from BIP-39 mnemonic)
     * @param path The derivation path (e.g., "m/44'/501'/0'/0'")
     * @return 32-byte private key (seed)
     */
    fun deriveKey(seed: ByteArray, path: String): ByteArray {
        val parts = path.split("/")
        if (parts[0] != "m") throw IllegalArgumentException("Derivation path must start with 'm'")

        var (privateKey, chainCode) = getMasterKeyFromSeed(seed)

        for (i in 1 until parts.size) {
            val part = parts[i]
            val hardened = part.endsWith("'")
            if (!hardened) {
                // Wipe if we error out
                privateKey.fill(0)
                chainCode.fill(0)
                throw IllegalArgumentException("Ed25519 derivation only supports hardened paths (e.g., 44')")
            }

            val index = part.removeSuffix("'").toLong()
            val (childPriv, childChain) = privateKey.use { pk ->
                chainCode.use { cc ->
                    deriveChildKey(pk, cc, index or 0x80000000L)
                }
            }

            privateKey = childPriv
            chainCode = childChain
        }
        
        chainCode.fill(0)
        return privateKey
    }

    private fun getMasterKeyFromSeed(seed: ByteArray): Pair<ByteArray, ByteArray> {
        val mac = Mac.getInstance(HMAC_SHA512)
        mac.init(SecretKeySpec(ED25519_SEED, HMAC_SHA512))
        val result = mac.doFinal(seed)
        return result.sliceArray(0 until 32) to result.sliceArray(32 until 64)
    }

    private fun deriveChildKey(
        privKey: ByteArray,
        chainCode: ByteArray,
        index: Long
    ): Pair<ByteArray, ByteArray> {
        val mac = Mac.getInstance(HMAC_SHA512)
        mac.init(SecretKeySpec(chainCode, HMAC_SHA512))

        val data = ByteArray(37)
        data[0] = 0x00
        System.arraycopy(privKey, 0, data, 1, 32)
        
        // Big-endian index
        data[33] = (index shr 24 and 0xFF).toByte()
        data[34] = (index shr 16 and 0xFF).toByte()
        data[35] = (index shr 8 and 0xFF).toByte()
        data[36] = (index and 0xFF).toByte()

        val result = mac.doFinal(data)
        return result.sliceArray(0 until 32) to result.sliceArray(32 until 64)
    }
}
