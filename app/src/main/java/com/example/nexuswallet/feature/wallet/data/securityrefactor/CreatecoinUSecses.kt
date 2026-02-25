package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
import com.example.nexuswallet.feature.coin.solana.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
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
class CreateWalletUseCase @Inject constructor(
    private val walletLocalDataSource: WalletLocalDataSource,
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {

    suspend operator fun invoke(
        mnemonic: List<String>,
        name: String,
        includeBitcoin: Boolean = true,
        includeEthereum: Boolean = true,
        includeSolana: Boolean = true,
        includeUSDC: Boolean = false,
        ethereumNetwork: EthereumNetwork = EthereumNetwork.Sepolia,
        bitcoinNetwork: BitcoinNetwork = BitcoinNetwork.TESTNET,
        solanaNetwork: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<Wallet> {
        return try {
            val walletId = "wallet_${System.currentTimeMillis()}"

            // Create coins
            val bitcoinCoin = if (includeBitcoin) {
                createBitcoinCoin(mnemonic, bitcoinNetwork)
                    ?: return Result.Error("Failed to create Bitcoin coin")
            } else null

            val ethereumCoin = if (includeEthereum) {
                createEthereumCoin(mnemonic, ethereumNetwork)
                    ?: return Result.Error("Failed to create Ethereum coin")
            } else null

            val solanaCoin = if (includeSolana) {
                createSolanaCoin(mnemonic, solanaNetwork)
                    ?: return Result.Error("Failed to create Solana coin")
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
                return Result.Error("Failed to secure mnemonic")
            }

            // Store Bitcoin private key if included
            if (includeBitcoin && bitcoinCoin != null) {
                val privateKey = deriveBitcoinPrivateKey(mnemonic, bitcoinNetwork)
                if (privateKey == null || !storePrivateKey(walletId, "BTC_PRIVATE_KEY", privateKey)) {
                    return Result.Error("Failed to store Bitcoin private key")
                }
            }

            // Store Ethereum private key if included
            if (includeEthereum && ethereumCoin != null) {
                val privateKey = deriveEthereumPrivateKey(mnemonic)
                if (privateKey == null || !storePrivateKey(walletId, "ETH_PRIVATE_KEY", privateKey)) {
                    return Result.Error("Failed to store Ethereum private key")
                }
            }

            // Store Solana private key if included
            if (includeSolana && solanaCoin != null) {
                val privateKey = deriveSolanaPrivateKey(mnemonic)
                if (privateKey == null || !storePrivateKey(walletId, "SOLANA_PRIVATE_KEY", privateKey)) {
                    return Result.Error("Failed to store Solana private key")
                }
            }

            // Save wallet to database
            try {
                walletLocalDataSource.saveWallet(wallet)
            } catch (e: Exception) {
                return Result.Error("Failed to save wallet: ${e.message}", e)
            }

            Result.Success(wallet)
        } catch (e: Exception) {
            Result.Error("Wallet creation failed: ${e.message}", e)
        }
    }

    // ============ PRIVATE COIN CREATION METHODS ============

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

            bitcoinCoin
        } catch (e: Exception) {
            null
        }
    }

    private fun createEthereumCoin(mnemonic: List<String>, network: EthereumNetwork): EthereumCoin? {
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

            ethereumCoin
        } catch (e: Exception) {
            null
        }
    }

    private fun createSolanaCoin(mnemonic: List<String>, network: SolanaNetwork): SolanaCoin? {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val keypair = deriveSolanaKeypairFromSeed(seed)

            val privateKeyHex = keypair.secret.joinToString("") { "%02x".format(it) }

            // Validate the generated private key
            if (!KeyValidation.isValidSolanaPrivateKey(privateKeyHex)) {
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

            solanaCoin
        } catch (e: Exception) {
            null
        }
    }

    // ============ PRIVATE KEY DERIVATION METHODS ============

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
            null
        }
    }

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
            null
        }
    }

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
            null
        }
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
            false
        }
    }

    // ============ HELPER METHODS ============

    private fun deriveSolanaKeypairFromSeed(seed: ByteArray): Keypair {
        val hash = MessageDigest.getInstance("SHA-256").digest(seed)

        val expandedSeed = ByteArray(64)
        System.arraycopy(hash, 0, expandedSeed, 0, 32)

        val secondHash = MessageDigest.getInstance("SHA-256").digest(hash)
        System.arraycopy(secondHash, 0, expandedSeed, 32, 32)

        return Keypair.fromSecretKey(expandedSeed)
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val HARDENED_BIT = 0x80000000
    }
}