package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_MAINNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_TESTNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_ETHEREUM_MAIN
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_DEVNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_MAINNET
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.datasource.WalletDataSource
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
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
    private val logger: Logger
) {

    private val tag = "CreateWalletUC"

    suspend operator fun invoke(
        mnemonic: List<String>,
        name: String,
        includeBitcoinMainnet: Boolean,
        includeBitcoinTestnet: Boolean,
        includeEthereumMainnet: Boolean,
        includeEthereumSepolia: Boolean,
        includeSolanaMainnet: Boolean,
        includeSolanaDevnet: Boolean,
        includeUSDCMainnet: Boolean,
        includeUSDCSepolia: Boolean,
        includeUSDTMainnet: Boolean
    ): Result<Wallet> {
        logger.d(
            tag,
            "Creating wallet: $name, " +
                    "Bitcoin: Mainnet=$includeBitcoinMainnet, Testnet=$includeBitcoinTestnet, " +
                    "Ethereum: Mainnet=$includeEthereumMainnet, Sepolia=$includeEthereumSepolia, " +
                    "Solana: Mainnet=$includeSolanaMainnet, Devnet=$includeSolanaDevnet, " +
                    "USDC: Mainnet=$includeUSDCMainnet, Sepolia=$includeUSDCSepolia, " +
                    "USDT: Mainnet=$includeUSDTMainnet"
        )

        val walletId = "wallet_${System.currentTimeMillis()}"
        val bitcoinCoins = mutableListOf<BitcoinCoin>()
        val solanaCoins = mutableListOf<SolanaCoin>()
        val evmTokens = mutableListOf<EVMToken>()

        // Bitcoin Mainnet
        if (includeBitcoinMainnet) {
            createBitcoinCoin(mnemonic, BitcoinNetwork.Mainnet)?.let { coin ->
                bitcoinCoins.add(coin)
                logger.d(tag, "Bitcoin Mainnet coin created")
            } ?: return Result.Error("Failed to create Bitcoin Mainnet coin").also {
                logger.e(tag, "Failed to create Bitcoin Mainnet coin")
            }
        }

        // Bitcoin Testnet
        if (includeBitcoinTestnet) {
            createBitcoinCoin(mnemonic, BitcoinNetwork.Testnet)?.let { coin ->
                bitcoinCoins.add(coin)
                logger.d(tag, "Bitcoin Testnet coin created")
            } ?: return Result.Error("Failed to create Bitcoin Testnet coin").also {
                logger.e(tag, "Failed to create Bitcoin Testnet coin")
            }
        }

        // Ethereum Mainnet
        if (includeEthereumMainnet) {
            createNativeETH(mnemonic, EthereumNetwork.Mainnet)?.let { nativeEth ->
                evmTokens.add(nativeEth)
                logger.d(tag, "Ethereum Mainnet coin created")

                // Create USDC on Mainnet if requested
                if (includeUSDCMainnet) {
                    val usdcToken = createUSDCToken(nativeEth)
                    evmTokens.add(usdcToken)
                    logger.d(tag, "USDC Mainnet token created")
                }

                // Create USDT on Mainnet if requested
                if (includeUSDTMainnet) {
                    val usdtToken = createUSDTToken(nativeEth)
                    evmTokens.add(usdtToken)
                    logger.d(tag, "USDT Mainnet token created")
                }
            } ?: return Result.Error("Failed to create Ethereum Mainnet coin").also {
                logger.e(tag, "Failed to create Ethereum Mainnet coin")
            }
        }

        // Ethereum Sepolia (Native ETH - Testnet)
        if (includeEthereumSepolia) {
            createNativeETH(mnemonic, EthereumNetwork.Sepolia)?.let { nativeEth ->
                evmTokens.add(nativeEth)
                logger.d(tag, "Ethereum Sepolia coin created")

                // Create USDC on Sepolia if requested
                if (includeUSDCSepolia) {
                    val usdcToken = createUSDCToken(nativeEth)
                    evmTokens.add(usdcToken)
                    logger.d(tag, "USDC Sepolia token created")
                }
            } ?: return Result.Error("Failed to create Ethereum Sepolia coin").also {
                logger.e(tag, "Failed to create Ethereum Sepolia coin")
            }
        }

        // Solana Mainnet
        if (includeSolanaMainnet) {
            createSolanaCoin(mnemonic, SolanaNetwork.Mainnet)?.let { coin ->
                solanaCoins.add(coin)
                logger.d(tag, "Solana Mainnet coin created")
            } ?: return Result.Error("Failed to create Solana Mainnet coin").also {
                logger.e(tag, "Failed to create Solana Mainnet coin")
            }
        }

        // Solana Devnet
        if (includeSolanaDevnet) {
            createSolanaCoin(mnemonic, SolanaNetwork.Devnet)?.let { coin ->
                solanaCoins.add(coin)
                logger.d(tag, "Solana Devnet coin created")
            } ?: return Result.Error("Failed to create Solana Devnet coin").also {
                logger.e(tag, "Failed to create Solana Devnet coin")
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
        val mnemonicString = mnemonic.joinToString(" ")
        val (encryptedHex, ivHex) = keyStoreRepository.encryptString(mnemonicString)
        securityPreferencesRepository.storeEncryptedMnemonic(
            walletId = walletId,
            encryptedMnemonic = encryptedHex,
            iv = ivHex.hexToByteArray()
        )
        logger.d(tag, "Mnemonic secured successfully")

        // Store Bitcoin private keys
        bitcoinCoins.forEach { coin ->
            val keyType = when (coin.network) {
                BitcoinNetwork.Mainnet -> KEY_BITCOIN_MAINNET
                BitcoinNetwork.Testnet -> KEY_BITCOIN_TESTNET
            }
            val privateKey = deriveBitcoinPrivateKey(mnemonic, coin.network)
                ?: return Result.Error("Failed to derive Bitcoin private key")

            val (encryptedKeyHex, keyIvHex) = keyStoreRepository.encryptString(privateKey)
            securityPreferencesRepository.storeEncryptedPrivateKey(
                walletId = walletId,
                keyType = keyType,
                encryptedKey = encryptedKeyHex,
                iv = keyIvHex.hexToByteArray()
            )
            logger.d(tag, "Bitcoin private key stored for ${coin.network}")
        }

        // Store Ethereum private key
        if (evmTokens.isNotEmpty()) {
            val privateKey = deriveEthereumPrivateKey(mnemonic)
                ?: return Result.Error("Failed to derive Ethereum private key")

            val (encryptedKeyHex, keyIvHex) = keyStoreRepository.encryptString(privateKey)
            securityPreferencesRepository.storeEncryptedPrivateKey(
                walletId = walletId,
                keyType = KEY_ETHEREUM_MAIN,
                encryptedKey = encryptedKeyHex,
                iv = keyIvHex.hexToByteArray()
            )
            logger.d(tag, "Ethereum private key stored successfully")
        }

        // Store Solana private keys
        solanaCoins.forEach { coin ->
            val keyType = when (coin.network) {
                SolanaNetwork.Mainnet -> KEY_SOLANA_MAINNET
                SolanaNetwork.Devnet -> KEY_SOLANA_DEVNET
            }
            val privateKey = deriveSolanaPrivateKey(mnemonic, coin.derivationPath)
                ?: return Result.Error("Failed to derive Solana private key")

            val (encryptedKeyHex, keyIvHex) = keyStoreRepository.encryptString(privateKey)
            securityPreferencesRepository.storeEncryptedPrivateKey(
                walletId = walletId,
                keyType = keyType,
                encryptedKey = encryptedKeyHex,
                iv = keyIvHex.hexToByteArray()
            )
            logger.d(tag, "Solana private key stored for ${coin.network}")
        }

        // Save wallet to database
        try {
            walletDataSource.saveWallet(wallet)
            logger.d(tag, "Wallet saved to database successfully: $walletId")
        } catch (e: Exception) {
            logger.e(tag, "Failed to save wallet to database", e)
            return Result.Error("Failed to save wallet: ${e.message}", e)
        }

        logger.d(tag, "Wallet created successfully: $walletId")
        return Result.Success(wallet)
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
            logger.d(tag, "Bitcoin $network address created: $address")
        }

    } catch (e: Exception) {
        logger.e(tag, "Bitcoin coin creation failed for $network", e)
        null
    }

    private fun createNativeETH(
        mnemonic: List<String>,
        network: EthereumNetwork
    ): NativeETH? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val credentials = deriveEthereumCredentials(seed)

            NativeETH(
                address = credentials.address,
                publicKey = credentials.ecKeyPair.publicKey.toString(16),
                network = network
            )
        } catch (e: Exception) {
            logger.e(tag, "Failed to create Native ETH for $network", e)
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
            contractAddress = when (nativeEth.network) {
                EthereumNetwork.Mainnet -> nativeEth.network.usdtContractAddress
                EthereumNetwork.Sepolia -> nativeEth.network.usdtContractAddress
            }
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

            SolanaCoin(
                address = keypair.publicKey.toString(),
                publicKey = keypair.publicKey.toString(),
                network = network,
                derivationPath = derivationPath,
                splTokens = emptyList()
            ).also {
                logger.d(
                    tag,
                    "Solana $network coin created with address: ${it.address.take(8)}... using path: $derivationPath"
                )
            }
        } catch (e: Exception) {
            logger.e(tag, "Failed to create Solana coin for $network", e)
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
            logger.e(tag, "Failed to derive Bitcoin private key for $network", e)
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

    private fun deriveEthereumPrivateKey(mnemonic: List<String>): String? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val credentials = deriveEthereumCredentials(seed)
            "0x${credentials.ecKeyPair.privateKey.toString(16)}"
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
        // THIS IS A TEMPORARY SOLUTION TODO: use a proper HD derivation library in production
        val pathSeed = seed + derivationPath.toByteArray()
        val expandedSeed = deriveSolanaExpandedSeed(pathSeed)
        return Keypair.fromSecretKey(expandedSeed)
    }

    private fun deriveSolanaPrivateKey(mnemonic: List<String>, derivationPath: String): String? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val pathSeed = seed + derivationPath.toByteArray()
            val expandedSeed = deriveSolanaExpandedSeed(pathSeed)
            expandedSeed.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.e(tag, "Failed to derive Solana private key", e)
            null
        }
    }

    private fun deriveSolanaExpandedSeed(seed: ByteArray): ByteArray {
        val hash = MessageDigest.getInstance(HASH_ALGORITHM_SHA256).digest(seed)
        val expandedSeed = ByteArray(EXPANDED_SEED_SIZE_64)
        System.arraycopy(hash, ARRAY_START_INDEX, expandedSeed, ARRAY_START_INDEX, HASH_SIZE_32)
        val secondHash = MessageDigest.getInstance(HASH_ALGORITHM_SHA256).digest(hash)
        System.arraycopy(secondHash, ARRAY_START_INDEX, expandedSeed, HASH_SIZE_32, HASH_SIZE_32)
        return expandedSeed
    }

    companion object {
        private const val HARDENED_BIT = 0x80000000
        private const val ETHEREUM_DERIVATION_PATH = "m/44'/60'/0'/0/0"
        private const val SOLANA_MAINNET_DERIVATION_PATH = "m/44'/501'/0'/0'"
        private const val SOLANA_DEVNET_DERIVATION_PATH = "m/44'/501'/1'/0'"
        private const val HASH_ALGORITHM_SHA256 = "SHA-256"
        private const val EXPANDED_SEED_SIZE_64 = 64
        private const val HASH_SIZE_32 = 32
        private const val ARRAY_START_INDEX = 0
    }
}