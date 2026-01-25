package com.example.nexuswallet.feature.wallet.domain


import android.content.Context
import com.example.nexuswallet.data.model.BitcoinNetwork
import com.example.nexuswallet.data.model.BitcoinWallet
import com.example.nexuswallet.data.model.EthereumNetwork
import com.example.nexuswallet.data.model.EthereumWallet
import com.example.nexuswallet.data.model.MultiChainWallet
import com.example.nexuswallet.data.model.WalletType
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.wallet.DeterministicSeed
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import java.security.SecureRandom
import kotlin.collections.map
import org.bitcoinj.wallet.Wallet as BitcoinJWallet

class WalletManager(private val context: Context) {

    // Generate BIP39 mnemonic
    fun generateMnemonic(wordCount: Int = 12): List<String> {
        val strength = when (wordCount) {
            12 -> 128
            15 -> 160
            18 -> 192
            21 -> 224
            24 -> 256
            else -> 128
        }

        val entropy = ByteArray(strength / 8)
        SecureRandom().nextBytes(entropy)

        return try {
            MnemonicUtils.generateMnemonic(entropy).split(" ")
        } catch (e: Exception) {
            // Fallback to BitcoinJ
            MnemonicCode.INSTANCE.toMnemonic(entropy)
        }
    }

    fun validateMnemonic(mnemonic: List<String>): Boolean {
        return try {
            MnemonicUtils.validateMnemonic(mnemonic.joinToString(" "))
        } catch (e: Exception) {
            false
        }
    }

    fun createBitcoinWallet(
        mnemonic: List<String>,
        name: String,
        network: BitcoinNetwork = BitcoinNetwork.MAINNET
    ): BitcoinWallet {
        val params = when (network) {
            BitcoinNetwork.MAINNET -> MainNetParams.get()
            BitcoinNetwork.TESTNET -> TestNet3Params.get()
            BitcoinNetwork.REGTEST -> MainNetParams.get()
        }

        val seed = DeterministicSeed(mnemonic, null, "", 0L)
        val wallet = BitcoinJWallet.fromSeed(params, seed)
        val key = wallet.currentReceiveKey()

        // Generate xpub (extended public key)
        val xpub = wallet.watchingKey.serializePubB58(params)

        val address = LegacyAddress.fromKey(params, key).toString()

        return BitcoinWallet(
            id = "btc_${System.currentTimeMillis()}",
            name = name,
            address = address,
            publicKey = key.pubKey.toString(),
            privateKeyEncrypted = "", // Will be encrypted separately
            network = network,
            derivationPath = "m/44'/0'/0'/0/0",
            xpub = xpub,
            mnemonicHash = mnemonic.hashCode().toString(),
            createdAt = System.currentTimeMillis(),
            isBackedUp = false,
            walletType = WalletType.BITCOIN
        )
    }

    fun createEthereumWallet(
        mnemonic: List<String>,
        name: String,
        network: EthereumNetwork = EthereumNetwork.MAINNET
    ): EthereumWallet {
        val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")

        // Derive based on network
        val derivationPath = when (network) {
            EthereumNetwork.MAINNET -> "m/44'/60'/0'/0/0"
            EthereumNetwork.POLYGON -> "m/44'/966'/0'/0/0"
            EthereumNetwork.BSC -> "m/44'/9006'/0'/0/0"
            EthereumNetwork.ARBITRUM -> "m/44'/60'/0'/0/0"
            EthereumNetwork.OPTIMISM -> "m/44'/60'/0'/0/0"
            else -> "m/44'/60'/0'/0/0"
        }

        val pathArray = derivationPath.split("/")
            .drop(1)
            .map { part ->
                val isHardened = part.endsWith("'")
                val number = part.replace("'", "").toInt()
                if (isHardened) number or HARDENED_BIT else number
            }
            .toIntArray()

        val masterKey = Bip32ECKeyPair.generateKeyPair(seed)
        val derivedKey = Bip32ECKeyPair.deriveKeyPair(masterKey, pathArray)
        val credentials = Credentials.create(derivedKey)

        return EthereumWallet(
            id = "eth_${System.currentTimeMillis()}",
            name = name,
            address = credentials.address,
            publicKey = derivedKey.publicKeyPoint.getEncoded(false).toHex(),
            privateKeyEncrypted = "", // Will be encrypted separately
            network = network,
            derivationPath = derivationPath,
            isSmartContractWallet = false,
            walletFile = null,
            mnemonicHash = mnemonic.hashCode().toString(),
            createdAt = System.currentTimeMillis(),
            isBackedUp = false,
            walletType = WalletType.ETHEREUM
        )
    }

    fun createMultiChainWallet(
        mnemonic: List<String>,
        name: String
    ): MultiChainWallet {
        val bitcoinWallet = createBitcoinWallet(mnemonic, "$name (Bitcoin)")
        val ethereumWallet = createEthereumWallet(mnemonic, "$name (Ethereum)")
        val polygonWallet = createEthereumWallet(mnemonic, "$name (Polygon)").copy(
            network = EthereumNetwork.POLYGON
        )
        val bscWallet = createEthereumWallet(mnemonic, "$name (BSC)").copy(
            network = EthereumNetwork.BSC
        )

        // Use Ethereum address as the primary address, or Bitcoin if Ethereum is null
        val primaryAddress = ethereumWallet.address ?: bitcoinWallet.address

        return MultiChainWallet(
            id = "multi_${System.currentTimeMillis()}",
            name = name,
            address = primaryAddress,
            bitcoinWallet = bitcoinWallet,
            ethereumWallet = ethereumWallet,
            polygonWallet = polygonWallet,
            bscWallet = bscWallet,
            solanaWallet = null,
            mnemonicHash = mnemonic.hashCode().toString(),
            createdAt = System.currentTimeMillis(),
            isBackedUp = false,
            walletType = WalletType.MULTICHAIN
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val HARDENED_BIT = 0x80000000.toInt()
    }
}
