package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.domain.di.DefaultDispatcher
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.Slip10
import com.example.nexuswallet.feature.core.util.use
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_MAINNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_BITCOIN_TESTNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_ETHEREUM_MAIN
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_DEVNET
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_SOLANA_MAINNET
import com.example.nexuswallet.feature.core.util.toHex
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.logging.Logger
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
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateWalletUseCase @Inject constructor(
    private val walletDataSource: WalletDataSource,
    private val keyStoreRepository: KeyStoreRepository,
    private val vaultRepository: VaultRepository,
    private val logger: Logger,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        walletId: String,
        mnemonic: List<CharArray>,
        name: String,
        selectedNetworks: Set<Network>,
        selectedTokens: Map<EthereumNetwork, Set<EVMTokenType>>,
        cipher: javax.crypto.Cipher? = null
    ): Result<Wallet> = withContext(defaultDispatcher) {
        logger.d(TAG, "Starting wallet creation | walletId=$walletId, name=$name")
        
        val rawPrivateKeys = mutableMapOf<String, ByteArray>()

        val bitcoinCoins = mutableListOf<BitcoinCoin>()
        val solanaCoins = mutableListOf<SolanaCoin>()
        val evmTokens = mutableListOf<EVMToken>()

        // SECURITY: Generate master seed directly from CharArray without creating a joined String
        val masterSeed = generateMasterSeed(mnemonic)

        masterSeed.use { seed ->
            // 1. Pre-derive all addresses and keys
            // Process Bitcoin networks
            val bitcoinNetworks = selectedNetworks.filterIsInstance<BitcoinNetwork>()
            
            bitcoinNetworks.forEach { network ->
                createBitcoinCoin(mnemonic, network)?.let { coin ->
                    bitcoinCoins.add(coin)
                    val keyType = if (network == BitcoinNetwork.Mainnet) KEY_BITCOIN_MAINNET else KEY_BITCOIN_TESTNET
                    deriveBitcoinPrivateKey(mnemonic, network)?.let { rawKeys ->
                        rawPrivateKeys[keyType] = rawKeys
                    }
                    logger.d(TAG, "Bitcoin ${network.name} coin derived")
                } ?: run {
                    logger.e(TAG, "Failed to create Bitcoin ${network.name} coin")
                    return@withContext Result.Error("Failed to create Bitcoin ${network.name} coin")
                }
            }

            // Process Ethereum networks
            val ethereumNetworks = selectedNetworks.filterIsInstance<EthereumNetwork>()
            ethereumNetworks.forEach { network ->
                val credentials = deriveEthereumCredentials(seed)
                val nativeEth = NativeETH(
                    address = credentials.address,
                    publicKey = credentials.ecKeyPair.publicKey.toString(16),
                    network = network
                ).also {
                    logger.d(TAG, "Ethereum ${network.name} address generated: ${it.address.take(8)}...")
                }

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
            }

            if (evmTokens.isNotEmpty()) {
                val ethPrivateKey = deriveEthereumPrivateKey(seed)
                if (ethPrivateKey != null) {
                    rawPrivateKeys[KEY_ETHEREUM_MAIN] = ethPrivateKey
                }
            }

            // Process Solana networks
            val solanaNetworks = selectedNetworks.filterIsInstance<SolanaNetwork>()
            solanaNetworks.forEach { network ->
                val derivationPath = if (network == SolanaNetwork.Mainnet) SOLANA_MAINNET_DERIVATION_PATH else SOLANA_DEVNET_DERIVATION_PATH
                val keypair = deriveSolanaKeypairFromSeed(seed, derivationPath)

                val coin = SolanaCoin(
                    address = keypair.publicKey.toString(),
                    publicKey = keypair.publicKey.toString(),
                    network = network,
                    derivationPath = derivationPath,
                    splTokens = emptyList()
                ).also {
                    logger.d(TAG, "Solana ${network.name} address generated: ${it.address.take(8)}...")
                }

                solanaCoins.add(coin)

                val solPrivateKey = deriveSolanaPrivateKey(seed, derivationPath)
                if (solPrivateKey != null) {
                    val keyType = if (network == SolanaNetwork.Mainnet) KEY_SOLANA_MAINNET else KEY_SOLANA_DEVNET
                    rawPrivateKeys[keyType] = solPrivateKey
                }
            }
        }

        // 2. Encryption Batch (Hardware Sensitive)
        try {
            logger.d(TAG, "Securing wallet mnemonic and private keys")
            
            val encryptedPrivateKeys = mutableMapOf<String, Pair<String, ByteArray>>()
            
            // Secure mnemonic
            val encryptedMnemonicData = mnemonicToByteArray(mnemonic).use { bytes ->
                val mnemonicResult = if (cipher != null) {
                    keyStoreRepository.encryptWithCipher(cipher, bytes)
                } else {
                    keyStoreRepository.encrypt(bytes)
                }

                if (mnemonicResult is Result.Error) {
                    logger.e(TAG, "Mnemonic encryption failed | error=${mnemonicResult.message}")
                    return@withContext mnemonicResult
                }
                (mnemonicResult as Result.Success).data
            }

            val (encryptedMnemonic, mnemonicIv) = encryptedMnemonicData

            // Encrypt all pre-derived keys in sequence
            rawPrivateKeys.forEach { (keyType, rawKey) ->
                rawKey.use { key ->
                    val keyResult = keyStoreRepository.encrypt(key)
                    if (keyResult is Result.Error) {
                        throw Exception("Failed to encrypt $keyType: ${keyResult.message}")
                    }
                    val (encryptedData, iv) = (keyResult as Result.Success).data
                    encryptedPrivateKeys[keyType] = encryptedData.toHex() to iv
                }
            }

            // Save all secure data in one atomic Vault operation
            vaultRepository.storeSecurityBundle(
                walletId = walletId,
                mnemonic = encryptedMnemonic.toHex() to mnemonicIv,
                privateKeys = encryptedPrivateKeys
            )
            logger.d(TAG, "Security bundle secured in vault")

        } catch (e: Exception) {
            logger.e(TAG, "Encryption batch failed | error=${e.message}")
            return@withContext Result.Error("Failed to secure wallet: ${e.message}")
        } finally {
            // Safety: ensure all raw keys are wiped
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
            logger.d(TAG, "Wallet metadata saved to database")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to save wallet metadata | error=${e.message}")
            return@withContext Result.Error("Failed to save wallet: ${e.message}", e)
        }

        logger.d(TAG, "Wallet creation completed successfully")
        Result.Success(wallet)
    }

    private fun generateMasterSeed(mnemonic: List<CharArray>, passphrase: String = ""): ByteArray {
        val mnemonicChars = joinMnemonic(mnemonic)
        return try {
            val salt = ("mnemonic$passphrase").toByteArray(Charsets.UTF_8)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val spec = PBEKeySpec(mnemonicChars, salt, 2048, 512)
            factory.generateSecret(spec).encoded
        } finally {
            mnemonicChars.fill('\u0000')
        }
    }

    private fun joinMnemonic(mnemonic: List<CharArray>): CharArray {
        var totalLength = if (mnemonic.isEmpty()) 0 else mnemonic.size - 1
        for (word in mnemonic) totalLength += word.size
        
        val result = CharArray(totalLength)
        var offset = 0
        for (i in mnemonic.indices) {
            System.arraycopy(mnemonic[i], 0, result, offset, mnemonic[i].size)
            offset += mnemonic[i].size
            if (i < mnemonic.size - 1) {
                result[offset++] = ' '
            }
        }
        return result
    }

    private fun mnemonicToByteArray(mnemonic: List<CharArray>): ByteArray {
        var totalBytes = if (mnemonic.isEmpty()) 0 else mnemonic.size - 1
        for (word in mnemonic) {
            totalBytes += word.size
        }

        val result = ByteArray(totalBytes)
        var offset = 0
        for (i in mnemonic.indices) {
            for (j in mnemonic[i].indices) {
                result[offset++] = mnemonic[i][j].code.toByte()
            }
            if (i < mnemonic.size - 1) {
                result[offset++] = ' '.code.toByte()
            }
        }
        return result
    }

    private fun createBitcoinCoin(
        mnemonic: List<CharArray>,
        network: BitcoinNetwork
    ): BitcoinCoin? {
        // SECURITY: Convert to Strings only at the point of use for the library call
        val mnemonicStrings = mnemonic.map { String(it) }
        
        return try {
            val params = when (network) {
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
            }

            Context.propagate(Context(params))
            val seed = DeterministicSeed(mnemonicStrings, null, "", 0L)
            val wallet = org.bitcoinj.wallet.Wallet.fromSeed(params, seed, Script.ScriptType.P2PKH)

            val address = wallet.freshReceiveAddress().toString()
            val xpub = wallet.watchingKey.serializePubB58(params)

            BitcoinCoin(
                address = address,
                publicKey = wallet.watchingKey.pubKey.toString(),
                network = network,
                xpub = xpub
            ).also {
                logger.d(TAG, "Bitcoin ${network.name} address generated: ${address.take(8)}...")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Bitcoin address generation failed | network=${network.name}, error=${e.message}")
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

    private fun deriveBitcoinPrivateKey(mnemonic: List<CharArray>, network: BitcoinNetwork): ByteArray? {
        val originalContext = try {
            Context.get()
        } catch (e: IllegalStateException) {
            null
        }

        // SECURITY: Convert to Strings only at the point of use for the library call
        val mnemonicStrings = mnemonic.map { String(it) }

        return try {
            val params = when (network) {
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
            }

            val context = Context(params)
            Context.propagate(context)

            val seed = DeterministicSeed(mnemonicStrings, null, "", 0L)
            val wallet = org.bitcoinj.wallet.Wallet.fromSeed(params, seed, Script.ScriptType.P2PKH)
            val key = wallet.currentReceiveKey()
            key.privKeyBytes
        } catch (e: Exception) {
            logger.e(TAG, "Bitcoin private key derivation failed | network=${network.name}, error=${e.message}")
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

    private fun deriveEthereumPrivateKey(seed: ByteArray): ByteArray? {
        return try {
            val credentials = deriveEthereumCredentials(seed)

            // Extract private key as byte array
            val privateKey = credentials.ecKeyPair.privateKey.toByteArray()

            // If it has a leading zero (due to BigInteger sign), remove it to get 32 bytes
            if (privateKey.size == 33 && privateKey[0] == 0.toByte()) {
                privateKey.use { it.copyOfRange(1, 33) }
            } else {
                privateKey
            }
        } catch (e: Exception) {
            logger.e(TAG, "Ethereum private key derivation failed | error=${e.message}")
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

    private fun deriveSolanaPrivateKey(seed: ByteArray, derivationPath: String): ByteArray? {
        return try {
            Slip10.deriveKey(seed, derivationPath).use { derivedKey ->
                // We store the 64-byte secret key (seed + padded space for re-derivation)
                // to stay consistent with the expected format in SendSolanaUseCase.
                derivedKey + ByteArray(32)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Solana private key derivation failed | error=${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "CreateWalletUC"
        private const val HARDENED_BIT = 0x80000000
        private const val ETHEREUM_DERIVATION_PATH = "m/44'/60'/0'/0/0"
        private const val SOLANA_MAINNET_DERIVATION_PATH = "m/44'/501'/0'/0'"
        private const val SOLANA_DEVNET_DERIVATION_PATH = "m/44'/501'/0'/0'"
    }
}
