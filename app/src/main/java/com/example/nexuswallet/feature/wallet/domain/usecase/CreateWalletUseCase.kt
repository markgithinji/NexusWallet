package com.example.nexuswallet.feature.wallet.domain.usecase

import android.security.keystore.UserNotAuthenticatedException
import com.example.nexuswallet.feature.core.domain.di.DefaultDispatcher
import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.Slip10
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_MAINNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_TESTNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_ETHEREUM_MAIN
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_DEVNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_MAINNET
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.toHex
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.datasource.WalletDataSource
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Context
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.Script
import org.bitcoinj.wallet.DeterministicSeed
import org.sol4k.Keypair
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateWalletUseCase @Inject constructor(
    private val walletDataSource: WalletDataSource,
    private val keyStoreRepository: KeyStoreRepository,
    private val vaultRepository: VaultRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        walletId: String,
        mnemonic: List<String>,
        name: String,
        selectedNetworks: Set<Network>,
        selectedTokens: Map<EthereumNetwork, Set<EVMTokenType>>,
        cipher: javax.crypto.Cipher? = null
    ): Result<Wallet> = withContext(defaultDispatcher) {
        val rawPrivateKeys = mutableMapOf<String, ByteArray>()

        val bitcoinCoins = mutableListOf<BitcoinCoin>()
        val solanaCoins = mutableListOf<SolanaCoin>()
        val evmTokens = mutableListOf<EVMToken>()

        // 1. Pre-derive all addresses and keys
        // Process Bitcoin networks
        val bitcoinNetworks = selectedNetworks.filterIsInstance<BitcoinNetwork>()
        bitcoinNetworks.forEach { network ->
            createBitcoinCoin(mnemonic, network)?.let { coin ->
                bitcoinCoins.add(coin)
                deriveBitcoinPrivateKey(mnemonic, network)?.let { rawKeys ->
                    rawPrivateKeys[network.name] = rawKeys
                }
            } ?: return@withContext Result.Error("Failed to create Bitcoin ${network.name} coin")
        }

        // Process Ethereum networks
        val ethereumNetworks = selectedNetworks.filterIsInstance<EthereumNetwork>()
        ethereumNetworks.forEach { network ->
            createNativeETH(mnemonic, network)?.let { nativeEth ->
                evmTokens.add(nativeEth)
                
                // Add tokens
                val networkTokens = selectedTokens[network] ?: emptySet()
                networkTokens.forEach { tokenType ->
                    when (tokenType) {
                        EVMTokenType.USDC -> evmTokens.add(createUSDCToken(nativeEth))
                        EVMTokenType.USDT -> evmTokens.add(createUSDTToken(nativeEth))
                        else -> {}
                    }
                }
            } ?: return@withContext Result.Error("Failed to create Ethereum ${network.name} coin")
        }
        
        if (evmTokens.isNotEmpty()) {
            deriveEthereumPrivateKey(mnemonic)?.let { rawKeys ->
                rawPrivateKeys["ETHEREUM"] = rawKeys
            }
        }

        // Process Solana networks
        val solanaNetworks = selectedNetworks.filterIsInstance<SolanaNetwork>()
        solanaNetworks.forEach { network ->
            createSolanaCoin(mnemonic, network)?.let { coin ->
                solanaCoins.add(coin)
                deriveSolanaPrivateKey(mnemonic, coin.derivationPath)?.let { rawKeys ->
                    rawPrivateKeys[network.name] = rawKeys
                }
            } ?: return@withContext Result.Error("Failed to create Solana ${network.name} coin")
        }

        // 2. Encryption Batch (Hardware Sensitive)
        try {
            // Secure mnemonic
            val mnemonicBytes = mnemonicToByteArray(mnemonic)
            val (encryptedMnemonic, mnemonicIv) = keyStoreRepository.encrypt(mnemonicBytes)
            mnemonicBytes.fill(0)

            vaultRepository.storeEncryptedMnemonic(
                walletId = walletId,
                encryptedMnemonic = encryptedMnemonic.toHex(),
                iv = mnemonicIv
            )

            // Store Bitcoin keys
            bitcoinCoins.forEach { coin ->
                val rawKey = rawPrivateKeys[coin.network.name] ?: return@forEach
                val keyType = when (coin.network) {
                    BitcoinNetwork.Mainnet -> KEY_BITCOIN_MAINNET
                    BitcoinNetwork.Testnet -> KEY_BITCOIN_TESTNET
                }
                val (encryptedKey, keyIv) = keyStoreRepository.encrypt(rawKey)
                rawKey.fill(0)

                vaultRepository.storeEncryptedPrivateKey(
                    walletId = walletId,
                    keyType = keyType,
                    encryptedKey = encryptedKey.toHex(),
                    iv = keyIv
                )
            }

            // Store Ethereum key
            rawPrivateKeys["ETHEREUM"]?.let { rawKey ->
                val (encryptedKey, keyIv) = keyStoreRepository.encrypt(rawKey)
                rawKey.fill(0)

                vaultRepository.storeEncryptedPrivateKey(
                    walletId = walletId,
                    keyType = KEY_ETHEREUM_MAIN,
                    encryptedKey = encryptedKey.toHex(),
                    iv = keyIv
                )
            }

            // Store Solana keys
            solanaCoins.forEach { coin ->
                val rawKey = rawPrivateKeys[coin.network.name] ?: return@forEach
                val keyType = when (coin.network) {
                    SolanaNetwork.Mainnet -> KEY_SOLANA_MAINNET
                    SolanaNetwork.Devnet -> KEY_SOLANA_DEVNET
                }
                val (encryptedKey, keyIv) = keyStoreRepository.encrypt(rawKey)
                rawKey.fill(0)

                vaultRepository.storeEncryptedPrivateKey(
                    walletId = walletId,
                    keyType = keyType,
                    encryptedKey = encryptedKey.toHex(),
                    iv = keyIv
                )
            }
        } catch (e: Exception) {
            rawPrivateKeys.values.forEach { it.fill(0) }
            val isAuthRequired = e is UserNotAuthenticatedException ||
                    e.cause is UserNotAuthenticatedException ||
                    e is javax.crypto.IllegalBlockSizeException && e.message?.contains("user not authenticated", true) == true

            if (isAuthRequired) {
                return@withContext Result.Error(
                    message = "Authentication required to secure wallet",
                    throwable = HardwareAuthRequiredException(null)
                )
            }
            return@withContext Result.Error("Failed to secure wallet: ${e.message}")
        } finally {
            rawPrivateKeys.values.forEach { it.fill(0) }
        }

        // 3. Database Persistence
        val wallet = Wallet(
            id = walletId,
            name = name,
            mnemonicHash = mnemonic.hashCode().toString(),
            createdAt = System.currentTimeMillis(),
            isBackedUp = false,
            bitcoinCoins = bitcoinCoins,
            solanaCoins = solanaCoins,
            evmTokens = evmTokens
        )

        // Save wallet to database
        try {
            walletDataSource.saveWallet(wallet)
        } catch (e: Exception) {
            return@withContext Result.Error("Failed to save wallet: ${e.message}", e)
        }

        Result.Success(wallet)
    }

    private fun createBitcoinCoin(
        mnemonic: List<String>,
        network: BitcoinNetwork
    ): BitcoinCoin? = try {

        val params = when (network) {
            BitcoinNetwork.Mainnet -> MainNetParams.get()
            BitcoinNetwork.Testnet -> TestNet3Params.get()
        }

        Context.propagate(Context(params))

        val seed = DeterministicSeed(mnemonic, null, "", 0L)

        val wallet = org.bitcoinj.wallet.Wallet.fromSeed(
            params,
            seed,
            Script.ScriptType.P2PKH
        )

        val address = wallet.freshReceiveAddress().toString()
        val xpub = wallet.watchingKey.serializePubB58(params)

        BitcoinCoin(
            address = address,
            publicKey = wallet.watchingKey.pubKey.toString(),
            network = network,
            xpub = xpub
        )

    } catch (e: Exception) {
        null
    }

    private fun createNativeETH(
        mnemonic: List<String>,
        network: EthereumNetwork
    ): NativeETH? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val credentials = deriveEthereumCredentials(seed)
            seed.fill(0) // Clear seed

            NativeETH(
                address = credentials.address,
                publicKey = credentials.ecKeyPair.publicKey.toString(16),
                network = network
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun createUSDCToken(nativeEth: NativeETH): USDCToken {
        return USDCToken(
            address = nativeEth.address,
            publicKey = nativeEth.publicKey,
            network = nativeEth.network,
            contractAddress = nativeEth.network.usdcContractAddress
        )
    }

    private fun createUSDTToken(nativeEth: NativeETH): USDTToken {
        return USDTToken(
            address = nativeEth.address,
            publicKey = nativeEth.publicKey,
            network = nativeEth.network,
            contractAddress = nativeEth.network.usdtContractAddress
        )
    }

    private fun createSolanaCoin(
        mnemonic: List<String>,
        network: SolanaNetwork
    ): SolanaCoin? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")

            val derivationPath = when (network) {
                SolanaNetwork.Mainnet -> SOLANA_MAINNET_DERIVATION_PATH
                SolanaNetwork.Devnet -> SOLANA_DEVNET_DERIVATION_PATH
            }

            val keypair = deriveSolanaKeypairFromSeed(seed, derivationPath)
            seed.fill(0) // Clear seed

            SolanaCoin(
                address = keypair.publicKey.toString(),
                publicKey = keypair.publicKey.toString(),
                network = network,
                derivationPath = derivationPath,
                splTokens = emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveBitcoinPrivateKey(mnemonic: List<String>, network: BitcoinNetwork): ByteArray? {
        val originalContext = try {
            Context.get()
        } catch (e: IllegalStateException) {
            null
        }

        return try {
            val params = when (network) {
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
            }

            val context = Context(params)
            Context.propagate(context)

            val seed = DeterministicSeed(mnemonic, null, "", 0L)
            val wallet = org.bitcoinj.wallet.Wallet.fromSeed(params, seed)
            val key = wallet.currentReceiveKey()
            key.privKeyBytes
        } catch (e: Exception) {
            null
        } finally {
            if (originalContext != null) {
                try {
                    Context.propagate(originalContext)
                } catch (e: Exception) {
                    // Ignore
                }
            } else {
                try {
                    Context.propagate(null)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun deriveEthereumPrivateKey(mnemonic: List<String>): ByteArray? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val credentials = deriveEthereumCredentials(seed)
            seed.fill(0) // Clear seed after derivation

            // Extract private key as byte array
            val privateKey = credentials.ecKeyPair.privateKey.toByteArray()

            // If it has a leading zero (due to BigInteger sign), remove it to get 32 bytes
            if (privateKey.size == 33 && privateKey[0] == 0.toByte()) {
                val result = privateKey.copyOfRange(1, 33)
                privateKey.fill(0)
                result
            } else {
                privateKey
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveEthereumCredentials(seed: ByteArray): Credentials {
        val pathArray = ETHEREUM_DERIVATION_PATH.split("/")
            .drop(1)
            .map { part ->
                val isHardened = part.endsWith("'")
                val number = part.replace("'", "").toInt()
                if (isHardened) number or HARDENED_BIT.toInt() else number
            }
            .toIntArray()

        val masterKey = Bip32ECKeyPair.generateKeyPair(seed)
        val derivedKey = Bip32ECKeyPair.deriveKeyPair(masterKey, pathArray)
        return Credentials.create(derivedKey)
    }

    private fun deriveSolanaKeypairFromSeed(seed: ByteArray, derivationPath: String): Keypair {
        val derivedKey = Slip10.deriveKey(seed, derivationPath)
        // Solana secret keys are [32-byte seed] + [32-byte public key].
        // sol4k's fromSecretKey re-derives the public key if we provide the seed.
        return Keypair.fromSecretKey(derivedKey + ByteArray(32))
    }

    private fun deriveSolanaPrivateKey(mnemonic: List<String>, derivationPath: String): ByteArray? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val derivedKey = Slip10.deriveKey(seed, derivationPath)
            seed.fill(0)

            // We store the 64-byte secret key (seed + padded space for re-derivation)
            // to stay consistent with the expected format in SendSolanaUseCase.
            val secretKey = derivedKey + ByteArray(32)
            derivedKey.fill(0)

            secretKey
        } catch (e: Exception) {
            null
        }
    }

    private fun mnemonicToByteArray(mnemonic: List<String>): ByteArray {
        var totalLength = 0
        mnemonic.forEachIndexed { index, word ->
            totalLength += word.length
            if (index < mnemonic.size - 1) totalLength += 1
        }
        val bytes = ByteArray(totalLength)
        var offset = 0
        mnemonic.forEachIndexed { index, word ->
            val wordBytes = word.toByteArray(Charsets.UTF_8)
            System.arraycopy(wordBytes, 0, bytes, offset, wordBytes.size)
            offset += wordBytes.size
            if (index < mnemonic.size - 1) {
                bytes[offset] = ' '.toByte()
                offset += 1
            }
            // Note: wordBytes is short-lived and will be GC'd, 
            // but we can't clear word itself as it's a String
        }
        return bytes
    }

    companion object {
        private const val HARDENED_BIT = 0x80000000
        private const val ETHEREUM_DERIVATION_PATH = "m/44'/60'/0'/0/0"
        private const val SOLANA_MAINNET_DERIVATION_PATH = "m/44'/501'/0'/0'"
        private const val SOLANA_DEVNET_DERIVATION_PATH = "m/44'/501'/0'/0'"
    }
}
