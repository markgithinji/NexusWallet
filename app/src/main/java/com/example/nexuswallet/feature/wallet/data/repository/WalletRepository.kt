package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.coin.solana.SolanaNetwork
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.crypto.ChildNumber.HARDENED_BIT
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.wallet.DeterministicSeed
import org.sol4k.Keypair
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import java.security.SecureRandom
import javax.inject.Inject
import org.bitcoinj.wallet.Wallet as BitcoinJWallet

class WalletRepository @Inject constructor(
    private val localDataSource: WalletLocalDataSource,
    private val securityManager: SecurityManager,
    private val keyManager: KeyManager
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _walletsFlow = MutableStateFlow<List<Wallet>>(emptyList())
    val walletsFlow: StateFlow<List<Wallet>> = _walletsFlow.asStateFlow()

    init {
        scope.launch {
            localDataSource.loadAllWallets().collect { wallets ->
                _walletsFlow.value = wallets
            }
        }
    }

    // === WALLET CREATION ===
    suspend fun createWallet(
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

            val bitcoinCoin =
                if (includeBitcoin) createBitcoinCoin(mnemonic, bitcoinNetwork) else null
            val ethereumCoin =
                if (includeEthereum) createEthereumCoin(mnemonic, ethereumNetwork) else null
            val solanaCoin = if (includeSolana) createSolanaCoin(solanaNetwork) else null

            val usdcCoin = if (includeUSDC && ethereumCoin != null) {
                USDCCoin(
                    address = ethereumCoin.address,
                    publicKey = ethereumCoin.publicKey,
                    network = ethereumCoin.network,
                    contractAddress = getUSDCContractAddress(ethereumCoin.network)
                )
            } else null

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

            // Store mnemonic
            securityManager.secureMnemonic(walletId, mnemonic)

            // Store private keys
            storePrivateKeys(wallet, mnemonic)

            // Save wallet
            localDataSource.saveWallet(wallet)

            Result.success(wallet)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // === WALLET CRUD ===

    suspend fun getWallet(walletId: String): Wallet? {
        return localDataSource.loadWallet(walletId)
    }

    suspend fun deleteWallet(walletId: String) {
        localDataSource.deleteWallet(walletId)
        removeWalletFromFlow(walletId)
    }

    // === BALANCE OPERATIONS ===
    suspend fun getWalletBalance(walletId: String): WalletBalance? {
        return localDataSource.loadWalletBalance(walletId)
    }


    // === MNEMONIC OPERATIONS ===
    fun generateNewMnemonic(wordCount: Int = 12): List<String> {
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

    // === HELPER METHODS ===
    private fun getUSDCContractAddress(network: EthereumNetwork): String {
        return network.usdcContractAddress
    }

    private fun removeWalletFromFlow(walletId: String) {
        val currentWallets = _walletsFlow.value.toMutableList()
        currentWallets.removeAll { it.id == walletId }
        _walletsFlow.value = currentWallets
    }

    private fun createBitcoinCoin(mnemonic: List<String>, network: BitcoinNetwork): BitcoinCoin {
        val params = when (network) {
            BitcoinNetwork.MAINNET -> MainNetParams.get()
            BitcoinNetwork.TESTNET -> TestNet3Params.get()
        }

        val seed = DeterministicSeed(mnemonic, null, "", 0L)
        val wallet = BitcoinJWallet.fromSeed(params, seed)
        val key = wallet.currentReceiveKey()
        val xpub = wallet.watchingKey.serializePubB58(params)
        val address = LegacyAddress.fromKey(params, key).toString()

        return BitcoinCoin(
            address = address,
            publicKey = key.pubKey.toString(),
            network = network,
            xpub = xpub
        )
    }

    private fun createEthereumCoin(mnemonic: List<String>, network: EthereumNetwork): EthereumCoin {
        val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")

        val derivationPath = when (network) {
            is EthereumNetwork.Mainnet -> "m/44'/60'/0'/0/0"
            is EthereumNetwork.Sepolia -> "m/44'/60'/0'/0/0"
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

        return EthereumCoin(
            address = credentials.address,
            publicKey = derivedKey.publicKeyPoint.getEncoded(false)
                .joinToString("") { "%02x".format(it) },
            network = network
        )
    }

    private fun createSolanaCoin(solanaNetwork: SolanaNetwork): SolanaCoin {
        val keypair = Keypair.generate()
        return SolanaCoin(
            address = keypair.publicKey.toString(),
            publicKey = keypair.publicKey.toString(),
            network = solanaNetwork
        )
    }

    private suspend fun storePrivateKeys(wallet: Wallet, mnemonic: List<String>) {
        wallet.bitcoin?.let { coin ->
            val params = when (coin.network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
            }
            val seed = DeterministicSeed(mnemonic, null, "", 0L)
            val btcWallet = BitcoinJWallet.fromSeed(params, seed)
            val key = btcWallet.currentReceiveKey()
            val privateKeyWIF = key.getPrivateKeyEncoded(params).toString()

            keyManager.storePrivateKey(
                walletId = wallet.id,
                privateKey = privateKeyWIF,
                keyType = "BTC_PRIVATE_KEY"
            )
        }

        wallet.ethereum?.let { coin ->
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val masterKey = Bip32ECKeyPair.generateKeyPair(seed)
            val pathArray = coin.derivationPath.split("/")
                .drop(1)
                .map { part ->
                    val isHardened = part.endsWith("'")
                    val number = part.replace("'", "").toInt()
                    if (isHardened) number or HARDENED_BIT else number
                }
                .toIntArray()
            val derivedKey = Bip32ECKeyPair.deriveKeyPair(masterKey, pathArray)
            val privateKeyHex = "0x${derivedKey.privateKey.toString(16)}"

            keyManager.storePrivateKey(
                walletId = wallet.id,
                privateKey = privateKeyHex,
                keyType = "ETH_PRIVATE_KEY"
            )
        }

        wallet.solana?.let { coin ->
            val keypair = Keypair.generate()
            val privateKeyHex = keypair.secret.joinToString("") { "%02x".format(it) }

            keyManager.storePrivateKey(
                walletId = wallet.id,
                privateKey = privateKeyHex,
                keyType = "SOLANA_PRIVATE_KEY"
            )
        }
    }
}