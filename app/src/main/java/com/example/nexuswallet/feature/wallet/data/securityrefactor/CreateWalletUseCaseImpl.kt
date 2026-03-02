package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.CreateWalletUseCase
import com.example.nexuswallet.feature.wallet.domain.WalletLocalDataSource
import org.bitcoinj.core.Address
import org.bitcoinj.core.Context
import org.bitcoinj.core.LegacyAddress
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
import org.bitcoinj.wallet.Wallet as BitcoinJWallet

@Singleton
class CreateWalletUseCaseImpl @Inject constructor(
    private val walletLocalDataSource: WalletLocalDataSource,
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : CreateWalletUseCase {

    private val tag = "CreateWalletUC"

    override suspend fun invoke(
        mnemonic: List<String>,
        name: String,
        // Bitcoin networks
        includeBitcoinMainnet: Boolean,
        includeBitcoinTestnet: Boolean,

        // Ethereum networks (Native ETH)
        includeEthereumMainnet: Boolean,
        includeEthereumSepolia: Boolean,

        // Solana networks
        includeSolanaMainnet: Boolean,
        includeSolanaDevnet: Boolean,

        // Tokens
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

        // ============ CREATE BITCOIN COINS ============

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

        // ============ CREATE ETHEREUM NATIVE TOKENS ============

        // Ethereum Mainnet (Native ETH)
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

        // ============ CREATE SOLANA COINS ============

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

        // ============ CREATE WALLET OBJECT ============

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

        // ============ SECURE MNEMONIC ============

        if (!secureMnemonic(walletId, mnemonic)) {
            logger.e(tag, "Failed to secure mnemonic")
            return Result.Error("Failed to secure mnemonic")
        }
        logger.d(tag, "Mnemonic secured successfully")

        // ============ STORE BITCOIN PRIVATE KEYS ============

        bitcoinCoins.forEach { coin ->
            val keyType = when (coin.network) {
                BitcoinNetwork.Mainnet -> "BTC_MAINNET_PRIVATE_KEY"
                BitcoinNetwork.Testnet -> "BTC_TESTNET_PRIVATE_KEY"
            }
            val privateKey = deriveBitcoinPrivateKey(mnemonic, coin.network)
            if (privateKey == null || !storePrivateKey(walletId, keyType, privateKey)) {
                logger.e(tag, "Failed to store Bitcoin private key for ${coin.network}")
                return Result.Error("Failed to store Bitcoin private key")
            }
            logger.d(tag, "Bitcoin private key stored for ${coin.network}")
        }

        // ============ STORE ETHEREUM PRIVATE KEYS ============

        // One private key works for all EVM tokens on all networks
        if (evmTokens.isNotEmpty()) {
            val privateKey = deriveEthereumPrivateKey(mnemonic)
            if (privateKey == null || !storePrivateKey(walletId, "ETH_MAIN_PRIVATE_KEY", privateKey)) {
                logger.e(tag, "Failed to store Ethereum private key")
                return Result.Error("Failed to store Ethereum private key")
            }
            logger.d(tag, "Ethereum private key stored successfully")
        }

        // ============ STORE SOLANA PRIVATE KEYS ============

        solanaCoins.forEach { coin ->
            val keyType = when (coin.network) {
                SolanaNetwork.Mainnet -> "SOL_MAINNET_PRIVATE_KEY"
                SolanaNetwork.Devnet -> "SOL_DEVNET_PRIVATE_KEY"
            }
            // Pass the derivation path to get the correct private key
            val privateKey = deriveSolanaPrivateKey(mnemonic, coin.derivationPath)
            if (privateKey == null || !storePrivateKey(walletId, keyType, privateKey)) {
                logger.e(tag, "Failed to store Solana private key for ${coin.network}")
                return Result.Error("Failed to store Solana private key")
            }
            logger.d(tag, "Solana private key stored for ${coin.network}")
        }

        // ============ SAVE WALLET TO DATABASE ============

        try {
            walletLocalDataSource.saveWallet(wallet)
            logger.d(tag, "Wallet saved to database successfully: $walletId")
        } catch (e: Exception) {
            logger.e(tag, "Failed to save wallet to database", e)
            return Result.Error("Failed to save wallet: ${e.message}", e)
        }

        logger.d(tag, "Wallet created successfully: $walletId")
        return Result.Success(wallet)
    }

    // ============ PRIVATE COIN CREATION METHODS ============

    private val bitcoinLock = Any()

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

        val wallet = BitcoinJWallet.fromSeed(
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

    private fun isValidBitcoinAddress(
        address: String,
        network: BitcoinNetwork
    ): Boolean = try {
        val params = when (network) {
            BitcoinNetwork.Mainnet -> MainNetParams.get()
            BitcoinNetwork.Testnet -> TestNet3Params.get()
        }
        Address.fromString(params, address)
        true
    } catch (e: Exception) {
        false
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
                EthereumNetwork.Mainnet -> "0xdAC17F958D2ee523a2206206994597C13D831ec7"
                EthereumNetwork.Sepolia -> "0x7169D38820dfd117C3FA1f22a697dBA58d90BA06"
            }
        )
    }

    private fun createSolanaCoin(
        mnemonic: List<String>,
        network: SolanaNetwork
    ): SolanaCoin? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")

            // Use different account indices for different networks
            val derivationPath = when (network) {
                SolanaNetwork.Mainnet -> "m/44'/501'/0'/0'"   // Standard path for Mainnet
                SolanaNetwork.Devnet -> "m/44'/501'/1'/0'"    // Different account for Devnet
            }

            val keypair = deriveSolanaKeypairFromSeed(seed, derivationPath)

            SolanaCoin(
                address = keypair.publicKey.toString(),
                publicKey = keypair.publicKey.toString(),
                network = network,
                derivationPath = derivationPath,
                splTokens = emptyList()
            ).also {
                logger.d(tag, "Solana $network coin created with address: ${it.address.take(8)}... using path: $derivationPath")
            }
        } catch (e: Exception) {
            logger.e(tag, "Failed to create Solana coin for $network", e)
            null
        }
    }

    // ============ PRIVATE KEY DERIVATION METHODS ============

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
            val wallet = BitcoinJWallet.fromSeed(params, seed)
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
        val derivationPath = "m/44'/60'/0'/0/0"
        val pathArray = derivationPath.split("/")
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
        val hash = MessageDigest.getInstance("SHA-256").digest(seed)
        val expandedSeed = ByteArray(64)
        System.arraycopy(hash, 0, expandedSeed, 0, 32)
        val secondHash = MessageDigest.getInstance("SHA-256").digest(hash)
        System.arraycopy(secondHash, 0, expandedSeed, 32, 32)
        return expandedSeed
    }

    // ============ SECURITY STORAGE METHODS ============

    private suspend fun secureMnemonic(walletId: String, mnemonic: List<String>): Boolean {
        return try {
            val mnemonicString = mnemonic.joinToString(" ")
            val (encryptedHex, ivHex) = keyStoreRepository.encryptString(mnemonicString)
            securityPreferencesRepository.storeEncryptedMnemonic(
                walletId = walletId,
                encryptedMnemonic = encryptedHex,
                iv = ivHex.hexToBytes()
            )
            true
        } catch (e: Exception) {
            logger.e(tag, "Failed to secure mnemonic", e)
            false
        }
    }

    private suspend fun storePrivateKey(
        walletId: String,
        keyType: String,
        privateKey: String
    ): Boolean {
        return try {
            if (!KeyValidation.validatePrivateKey(privateKey, keyType)) {
                logger.w(tag, "Private key validation failed for $keyType")
                return false
            }

            val (encryptedHex, ivHex) = keyStoreRepository.encryptString(privateKey)
            securityPreferencesRepository.storeEncryptedPrivateKey(
                walletId = walletId,
                keyType = keyType,
                encryptedKey = encryptedHex,
                iv = ivHex.hexToBytes()
            )
            true
        } catch (e: Exception) {
            logger.e(tag, "Failed to store private key for $keyType", e)
            false
        }
    }

    // ============ HELPER METHODS ============

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val HARDENED_BIT = 0x80000000
    }
}