package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_MAINNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_TESTNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_ETHEREUM_MAIN
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_DEVNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_MAINNET
import com.example.nexuswallet.feature.core.util.Slip10
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.toHex
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.core.domain.di.DefaultDispatcher
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
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
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateWalletUseCase @Inject constructor(
    private val walletDataSource: WalletDataSource,
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val tag = "CreateWalletUC"

    suspend operator fun invoke(
        mnemonic: List<String>,
        name: String,
        selectedNetworks: Set<Network>,
        selectedTokens: Map<EthereumNetwork, Set<EVMTokenType>>
    ): Result<Wallet> = withContext(defaultDispatcher) {
        val networkLog = selectedNetworks.joinToString { network ->
            when (network) {
                is BitcoinNetwork -> "Bitcoin - ${network.name}"
                is EthereumNetwork -> "Ethereum - ${network.name}"
                is SolanaNetwork -> "Solana - ${network.name}"
            }
        }

        val tokenLog = selectedTokens.flatMap { (network, tokens) ->
            tokens.map { tokenType ->
                "${tokenType.symbol} on ${network.name}"
            }
        }.joinToString()

        logger.d(tag, "Creating wallet: $name, Selected networks: $networkLog, Selected tokens: $tokenLog")

        val walletId = "wallet_${System.currentTimeMillis()}"
        val bitcoinCoins = mutableListOf<BitcoinCoin>()
        val solanaCoins = mutableListOf<SolanaCoin>()
        val evmTokens = mutableListOf<EVMToken>()

        // Process Bitcoin networks
        val bitcoinNetworks = selectedNetworks.filterIsInstance<BitcoinNetwork>()
        bitcoinNetworks.forEach { network ->
            createBitcoinCoin(mnemonic, network)?.let { coin ->
                bitcoinCoins.add(coin)
                logger.d(tag, "Bitcoin ${network.name} coin created")
            } ?: return@withContext Result.Error("Failed to create Bitcoin ${network.name} coin").also {
                logger.e(tag, "Failed to create Bitcoin ${network.name} coin")
            }
        }

        // Process Ethereum networks (Native ETH)
        val ethereumNetworks = selectedNetworks.filterIsInstance<EthereumNetwork>()
        ethereumNetworks.forEach { network ->
            createNativeETH(mnemonic, network)?.let { nativeEth ->
                evmTokens.add(nativeEth)
                logger.d(tag, "Ethereum ${network.name} coin created")

                // Create tokens on this network if any are selected
                val networkTokens = selectedTokens[network] ?: emptySet()
                networkTokens.forEach { tokenType ->
                    when (tokenType) {
                        EVMTokenType.USDC -> {
                            val usdcToken = createUSDCToken(nativeEth)
                            evmTokens.add(usdcToken)
                            logger.d(tag, "USDC token created on ${network.name}")
                        }
                        EVMTokenType.USDT -> {
                            val usdtToken = createUSDTToken(nativeEth)
                            evmTokens.add(usdtToken)
                            logger.d(tag, "USDT token created on ${network.name}")
                        }
                        EVMTokenType.NATIVE -> {
                            // Native ETH is already added via network selection, skip
                            logger.d(tag, "Native ETH already included for ${network.name}")
                        }
                    }
                }
            } ?: return@withContext Result.Error("Failed to create Ethereum ${network.name} coin").also {
                logger.e(tag, "Failed to create Ethereum ${network.name} coin")
            }
        }

        // Process Solana networks
        val solanaNetworks = selectedNetworks.filterIsInstance<SolanaNetwork>()
        solanaNetworks.forEach { network ->
            createSolanaCoin(mnemonic, network)?.let { coin ->
                solanaCoins.add(coin)
                logger.d(tag, "Solana ${network.name} coin created")
            } ?: return@withContext Result.Error("Failed to create Solana ${network.name} coin").also {
                logger.e(tag, "Failed to create Solana ${network.name} coin")
            }
        }

        // Validate at least one asset was created
        if (bitcoinCoins.isEmpty() && solanaCoins.isEmpty() && evmTokens.isEmpty()) {
            return@withContext Result.Error("No assets selected for wallet creation").also {
                logger.e(tag, "No assets selected for wallet creation")
            }
        }

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

        // Secure mnemonic
        val mnemonicBytes = mnemonic.joinToString(" ").toByteArray(Charsets.UTF_8)
        val (encryptedMnemonic, mnemonicIv) = keyStoreRepository.encrypt(mnemonicBytes)
        mnemonicBytes.fill(0) // Clear plaintext mnemonic

        securityPreferencesRepository.storeEncryptedMnemonic(
            walletId = walletId,
            encryptedMnemonic = encryptedMnemonic.toHex(),
            iv = mnemonicIv
        )
        logger.d(tag, "Mnemonic secured successfully")

        // Store Bitcoin private keys
        bitcoinCoins.forEach { coin ->
            val keyType = when (coin.network) {
                BitcoinNetwork.Mainnet -> KEY_BITCOIN_MAINNET
                BitcoinNetwork.Testnet -> KEY_BITCOIN_TESTNET
            }
            val privateKeyWIF = deriveBitcoinPrivateKey(mnemonic, coin.network)
                ?: return@withContext Result.Error("Failed to derive Bitcoin private key for ${coin.network.name}")

            val (encryptedKeyHex, keyIvHex) = keyStoreRepository.encryptString(privateKeyWIF)
            securityPreferencesRepository.storeEncryptedPrivateKey(
                walletId = walletId,
                keyType = keyType,
                encryptedKey = encryptedKeyHex,
                iv = keyIvHex.decodeHex()
            )
            logger.d(tag, "Bitcoin private key stored for ${coin.network.name}")
        }

        // Store Ethereum private key (same for all EVM networks)
        if (evmTokens.isNotEmpty()) {
            val privateKeyBytes = deriveEthereumPrivateKey(mnemonic)
                ?: return@withContext Result.Error("Failed to derive Ethereum private key")

            val (encryptedKey, keyIv) = keyStoreRepository.encrypt(privateKeyBytes)
            privateKeyBytes.fill(0) // Clear private key

            securityPreferencesRepository.storeEncryptedPrivateKey(
                walletId = walletId,
                keyType = KEY_ETHEREUM_MAIN,
                encryptedKey = encryptedKey.toHex(),
                iv = keyIv
            )
            logger.d(tag, "Ethereum private key stored successfully")
        }

        // Store Solana private keys
        solanaCoins.forEach { coin ->
            val keyType = when (coin.network) {
                SolanaNetwork.Mainnet -> KEY_SOLANA_MAINNET
                SolanaNetwork.Devnet -> KEY_SOLANA_DEVNET
            }
            val privateKeyBytes = deriveSolanaPrivateKey(mnemonic, coin.derivationPath)
                ?: return@withContext Result.Error("Failed to derive Solana private key for ${coin.network.name}")

            val (encryptedKey, keyIv) = keyStoreRepository.encrypt(privateKeyBytes)
            privateKeyBytes.fill(0) // Clear private key

            securityPreferencesRepository.storeEncryptedPrivateKey(
                walletId = walletId,
                keyType = keyType,
                encryptedKey = encryptedKey.toHex(),
                iv = keyIv
            )
            logger.d(tag, "Solana private key stored for ${coin.network.name}")
        }

        // Save wallet to database
        try {
            walletDataSource.saveWallet(wallet)
            logger.d(tag, "Wallet saved to database successfully: $walletId")
        } catch (e: Exception) {
            logger.e(tag, "Failed to save wallet to database", e)
            return@withContext Result.Error("Failed to save wallet: ${e.message}", e)
        }

        logger.d(tag, "Wallet created successfully: $walletId with ${bitcoinCoins.size} Bitcoin, ${solanaCoins.size} Solana, and ${evmTokens.size} EVM assets")
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
        ).also {
            logger.d(tag, "Bitcoin ${network.name} address created: ${address.take(8)}...")
        }

    } catch (e: Exception) {
        logger.e(tag, "Bitcoin coin creation failed for ${network.name}", e)
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
            logger.e(tag, "Failed to create Native ETH for ${network.name}", e)
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
            ).also {
                logger.d(
                    tag,
                    "Solana ${network.name} coin created with address: ${it.address.take(8)}... using path: $derivationPath"
                )
            }
        } catch (e: Exception) {
            logger.e(tag, "Failed to create Solana coin for ${network.name}", e)
            null
        }
    }

    private fun deriveBitcoinPrivateKey(mnemonic: List<String>, network: BitcoinNetwork): String? {
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
            key.getPrivateKeyEncoded(params).toString()
        } catch (e: Exception) {
            logger.e(tag, "Failed to derive Bitcoin private key for ${network.name}", e)
            null
        } finally {
            if (originalContext != null) {
                try {
                    Context.propagate(originalContext)
                } catch (e: Exception) {
                    logger.w(tag, "Failed to restore original context", e)
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
            logger.e(tag, "Failed to derive Ethereum private key", e)
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
            logger.e(tag, "Failed to derive Solana private key", e)
            null
        }
    }

    companion object {
        private const val HARDENED_BIT = 0x80000000
        private const val ETHEREUM_DERIVATION_PATH = "m/44'/60'/0'/0/0"
        private const val SOLANA_MAINNET_DERIVATION_PATH = "m/44'/501'/0'/0'"
        private const val SOLANA_DEVNET_DERIVATION_PATH = "m/44'/501'/0'/0'"
        private const val HASH_ALGORITHM_SHA256 = "SHA-256"
        private const val ARRAY_START_INDEX = 0
    }
}