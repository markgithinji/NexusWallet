package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
import com.example.nexuswallet.feature.coin.solana.SolanaNetwork
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.CreateWalletUseCase
import com.example.nexuswallet.feature.wallet.domain.WalletLocalDataSource
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
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
        includeBitcoin: Boolean,
        includeEthereum: Boolean,
        includeSolana: Boolean,
        includeUSDC: Boolean,
        ethereumNetwork: EthereumNetwork,
        bitcoinNetwork: BitcoinNetwork,
        solanaNetwork: SolanaNetwork
    ): Result<Wallet> {
        logger.d(
            tag,
            "Creating wallet: $name, Bitcoin: $includeBitcoin, Ethereum: $includeEthereum, Solana: $includeSolana, USDC: $includeUSDC"
        )

        val walletId = "wallet_${System.currentTimeMillis()}"

        // Create coins
        val bitcoinCoin = if (includeBitcoin) {
            createBitcoinCoin(mnemonic, bitcoinNetwork)
                ?: return Result.Error("Failed to create Bitcoin coin").also {
                    logger.e(tag, "Failed to create Bitcoin coin")
                }
        } else null

        val ethereumCoin = if (includeEthereum) {
            createEthereumCoin(mnemonic, ethereumNetwork)
                ?: return Result.Error("Failed to create Ethereum coin").also {
                    logger.e(tag, "Failed to create Ethereum coin")
                }
        } else null

        val solanaCoin = if (includeSolana) {
            createSolanaCoin(mnemonic, solanaNetwork)
                ?: return Result.Error("Failed to create Solana coin").also {
                    logger.e(tag, "Failed to create Solana coin")
                }
        } else null

        // Create USDC if requested
        val usdcCoin = if (includeUSDC && ethereumCoin != null) {
            USDCCoin(
                address = ethereumCoin.address,
                publicKey = ethereumCoin.publicKey,
                network = ethereumCoin.network,
                contractAddress = ethereumCoin.network.usdcContractAddress
            )
        } else null

        // Create wallet object
        val wallet = Wallet(
            id = walletId,
            name = name,
            mnemonicHash = mnemonic.hashCode().toString(),
            createdAt = System.currentTimeMillis(),
            isBackedUp = false,
            bitcoin = bitcoinCoin,
            ethereum = ethereumCoin,
            solana = solanaCoin,
            usdc = usdcCoin
        )

        // Store mnemonic securely
        if (!secureMnemonic(walletId, mnemonic)) {
            logger.e(tag, "Failed to secure mnemonic")
            return Result.Error("Failed to secure mnemonic")
        }
        logger.d(tag, "Mnemonic secured successfully")

        // Store Bitcoin private key if included
        if (includeBitcoin && bitcoinCoin != null) {
            val privateKey = deriveBitcoinPrivateKey(mnemonic, bitcoinNetwork)
            if (privateKey == null || !storePrivateKey(walletId, "BTC_PRIVATE_KEY", privateKey)) {
                logger.e(tag, "Failed to store Bitcoin private key")
                return Result.Error("Failed to store Bitcoin private key")
            }
            logger.d(tag, "Bitcoin private key stored successfully")
        }

        // Store Ethereum private key if included
        if (includeEthereum && ethereumCoin != null) {
            val privateKey = deriveEthereumPrivateKey(mnemonic)
            if (privateKey == null || !storePrivateKey(walletId, "ETH_PRIVATE_KEY", privateKey)) {
                logger.e(tag, "Failed to store Ethereum private key")
                return Result.Error("Failed to store Ethereum private key")
            }
            logger.d(tag, "Ethereum private key stored successfully")
        }

        // Store Solana private key if included
        if (includeSolana && solanaCoin != null) {
            val privateKey = deriveSolanaPrivateKey(mnemonic)
            if (privateKey == null || !storePrivateKey(
                    walletId,
                    "SOLANA_PRIVATE_KEY",
                    privateKey
                )
            ) {
                logger.e(tag, "Failed to store Solana private key")
                return Result.Error("Failed to store Solana private key")
            }
            logger.d(tag, "Solana private key stored successfully")
        }

        // Save wallet to database
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

    /**
     * Create Bitcoin coin from mnemonic
     */
    private fun createBitcoinCoin(mnemonic: List<String>, network: BitcoinNetwork): BitcoinCoin? {
        return try {
            val params = when (network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
            }

            val seed = DeterministicSeed(mnemonic, null, "", 0L)
            val wallet = BitcoinJWallet.fromSeed(params, seed)
            val key = wallet.currentReceiveKey()
            val privateKeyWIF = key.getPrivateKeyEncoded(params).toString()

            // Validate the generated private key
            if (!KeyValidation.isValidBitcoinPrivateKey(privateKeyWIF)) {
                logger.w(tag, "Invalid Bitcoin private key generated")
                KeyValidation.clearKeyFromMemory(privateKeyWIF)
                return null
            }

            val xpub = wallet.watchingKey.serializePubB58(params)
            val address = LegacyAddress.fromKey(params, key).toString()

            val bitcoinCoin = BitcoinCoin(
                address = address,
                publicKey = key.pubKey.toString(),
                network = network,
                xpub = xpub
            )

            // Clear sensitive data
            KeyValidation.clearKeyFromMemory(privateKeyWIF)

            logger.d(tag, "Bitcoin coin created for network: $network")
            bitcoinCoin
        } catch (e: Exception) {
            logger.e(tag, "Failed to create Bitcoin coin", e)
            null
        }
    }

    /**
     * Create Ethereum coin from mnemonic
     */
    private fun createEthereumCoin(
        mnemonic: List<String>,
        network: EthereumNetwork
    ): EthereumCoin? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")

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
            val privateKeyHex = "0x${derivedKey.privateKey.toString(16)}"

            // Validate the generated private key
            if (!KeyValidation.isValidEthereumPrivateKey(privateKeyHex)) {
                logger.w(tag, "Invalid Ethereum private key generated")
                KeyValidation.clearKeyFromMemory(privateKeyHex)
                return null
            }

            val credentials = Credentials.create(derivedKey)
            val ethereumCoin = EthereumCoin(
                address = credentials.address,
                publicKey = derivedKey.publicKeyPoint.getEncoded(false)
                    .joinToString("") { "%02x".format(it) },
                network = network
            )

            // Clear sensitive data
            KeyValidation.clearKeyFromMemory(privateKeyHex)

            logger.d(tag, "Ethereum coin created for network: $network")
            ethereumCoin
        } catch (e: Exception) {
            logger.e(tag, "Failed to create Ethereum coin", e)
            null
        }
    }

    /**
     * Create Solana coin from mnemonic
     */
    private fun createSolanaCoin(mnemonic: List<String>, network: SolanaNetwork): SolanaCoin? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val keypair = deriveSolanaKeypairFromSeed(seed)

            val privateKeyHex = keypair.secret.joinToString("") { "%02x".format(it) }

            // Validate the generated private key
            if (!KeyValidation.isValidSolanaPrivateKey(privateKeyHex)) {
                logger.w(tag, "Invalid Solana private key generated")
                KeyValidation.clearKeyFromMemory(privateKeyHex)
                return null
            }

            val solanaCoin = SolanaCoin(
                address = keypair.publicKey.toString(),
                publicKey = keypair.publicKey.toString(),
                network = network
            )

            // Clear sensitive data
            KeyValidation.clearKeyFromMemory(privateKeyHex)

            logger.d(tag, "Solana coin created for network: $network")
            solanaCoin
        } catch (e: Exception) {
            logger.e(tag, "Failed to create Solana coin", e)
            null
        }
    }

    // ============ PRIVATE KEY DERIVATION METHODS ============

    /**
     * Derive Bitcoin private key from mnemonic
     */
    private fun deriveBitcoinPrivateKey(mnemonic: List<String>, network: BitcoinNetwork): String? {
        return try {
            val params = when (network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
            }
            val seed = DeterministicSeed(mnemonic, null, "", 0L)
            val wallet = BitcoinJWallet.fromSeed(params, seed)
            val key = wallet.currentReceiveKey()
            key.getPrivateKeyEncoded(params).toString()
        } catch (e: Exception) {
            logger.e(tag, "Failed to derive Bitcoin private key", e)
            null
        }
    }

    /**
     * Derive Ethereum private key from mnemonic
     */
    private fun deriveEthereumPrivateKey(mnemonic: List<String>): String? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val masterKey = Bip32ECKeyPair.generateKeyPair(seed)

            val derivationPath = "m/44'/60'/0'/0/0"
            val pathArray = derivationPath.split("/")
                .drop(1)
                .map { part ->
                    val isHardened = part.endsWith("'")
                    val number = part.replace("'", "").toInt()
                    if (isHardened) number or HARDENED_BIT.toInt() else number
                }
                .toIntArray()

            val derivedKey = Bip32ECKeyPair.deriveKeyPair(masterKey, pathArray)
            "0x${derivedKey.privateKey.toString(16)}"
        } catch (e: Exception) {
            logger.e(tag, "Failed to derive Ethereum private key", e)
            null
        }
    }

    /**
     * Derive Solana private key from mnemonic
     */
    private fun deriveSolanaPrivateKey(mnemonic: List<String>): String? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val hash = MessageDigest.getInstance("SHA-256").digest(seed)

            val expandedSeed = ByteArray(64)
            System.arraycopy(hash, 0, expandedSeed, 0, 32)

            val secondHash = MessageDigest.getInstance("SHA-256").digest(hash)
            System.arraycopy(secondHash, 0, expandedSeed, 32, 32)

            expandedSeed.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.e(tag, "Failed to derive Solana private key", e)
            null
        }
    }

    // ============ SECURITY STORAGE METHODS ============

    /**
     * Securely store mnemonic phrase
     */
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

    /**
     * Store private key securely
     */
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

    /**
     * Derive Solana keypair from seed
     */
    private fun deriveSolanaKeypairFromSeed(seed: ByteArray): Keypair {
        val hash = MessageDigest.getInstance("SHA-256").digest(seed)

        val expandedSeed = ByteArray(64)
        System.arraycopy(hash, 0, expandedSeed, 0, 32)

        val secondHash = MessageDigest.getInstance("SHA-256").digest(hash)
        System.arraycopy(secondHash, 0, expandedSeed, 32, 32)

        return Keypair.fromSecretKey(expandedSeed)
    }

    /**
     * Convert hex string to byte array
     */
    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val HARDENED_BIT = 0x80000000
    }
}