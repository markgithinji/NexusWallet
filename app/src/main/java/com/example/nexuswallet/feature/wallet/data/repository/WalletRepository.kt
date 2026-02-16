package com.example.nexuswallet.feature.wallet.data.repository


import android.util.Log
import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository

import com.example.nexuswallet.feature.wallet.domain.WalletType
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.crypto.ChildNumber.HARDENED_BIT
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.wallet.DeterministicSeed
import org.sol4k.Keypair
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import org.bitcoinj.wallet.Wallet as BitcoinJWallet
@Singleton

class WalletRepository @Inject constructor(
    private val localDataSource: WalletLocalDataSource,
    private val securityManager: SecurityManager,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val keyManager: KeyManager
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _walletsFlow = MutableStateFlow<List<Wallet>>(emptyList())
    val walletsFlow: StateFlow<List<Wallet>> = _walletsFlow.asStateFlow()

    init {
        scope.launch {
            localDataSource.loadAllWallets().collect { wallets ->
                Log.d("WalletRepo", "Received ${wallets.size} wallets from data source")
                wallets.forEach { wallet ->
                    Log.d("WalletRepo", "  - Wallet: ${wallet.id} - ${wallet.name}")
                    Log.d("WalletRepo", "    BTC: ${wallet.bitcoin != null}, ETH: ${wallet.ethereum != null}, SOL: ${wallet.solana != null}, USDC: ${wallet.usdc != null}")
                }
                _walletsFlow.value = wallets
                wallets.forEach { wallet ->
                    syncWalletBalances(wallet)
                }
            }
        }
    }

    // === SYNC METHODS ===
    suspend fun syncWalletBalances(wallet: Wallet) {
        Log.d("WalletRepo", "=== SYNC WALLET BALANCES ===")
        Log.d("WalletRepo", "Wallet ID: ${wallet.id}")

        wallet.bitcoin?.let { syncBitcoinBalance(wallet.id, it) }
        wallet.ethereum?.let { syncEthereumBalance(wallet.id, it) }
        wallet.solana?.let { syncSolanaBalance(wallet.id, it) }
        wallet.usdc?.let { syncUSDCBalance(wallet.id, it) }
    }

    private suspend fun syncBitcoinBalance(walletId: String, coin: BitcoinCoin) {
        Log.d("WalletRepo", "Syncing Bitcoin balance for: ${coin.address}")

        val balanceResult = bitcoinBlockchainRepository.getBalance(
            address = coin.address,
            network = coin.network
        )

        when (balanceResult) {
            is com.example.nexuswallet.feature.coin.Result.Success -> {
                val btcBalance = balanceResult.data
                val satoshiBalance = (btcBalance * BigDecimal("100000000")).toBigInteger().toString()
                val usdValue = calculateUsdValue(btcBalance, "BTC")

                val currentBalance = localDataSource.loadWalletBalance(walletId) ?: WalletBalance(
                    walletId,
                    System.currentTimeMillis()
                )

                val updatedBalance = currentBalance.copy(
                    bitcoin = BitcoinBalance(
                        address = coin.address,
                        satoshis = satoshiBalance,
                        btc = btcBalance.setScale(8, RoundingMode.HALF_UP).toPlainString(),
                        usdValue = usdValue
                    ),
                    lastUpdated = System.currentTimeMillis()
                )

                localDataSource.saveWalletBalance(updatedBalance)
                Log.d("WalletRepo", " Bitcoin balance synced: $btcBalance BTC")
            }

            is com.example.nexuswallet.feature.coin.Result.Error -> {
                Log.e("WalletRepo", " Error syncing Bitcoin: ${balanceResult.message}")
            }
            else -> {}
        }
    }

    private suspend fun syncEthereumBalance(walletId: String, coin: EthereumCoin) {
        Log.d("WalletRepo", "Syncing Ethereum balance for: ${coin.address}")

        val balanceResult = ethereumBlockchainRepository.getEthereumBalance(
            address = coin.address,
            network = coin.network
        )

        when (balanceResult) {
            is com.example.nexuswallet.feature.coin.Result.Success -> {
                val ethBalance = balanceResult.data
                val weiBalance = (ethBalance * BigDecimal("1000000000000000000")).toBigInteger().toString()
                val usdValue = calculateUsdValue(ethBalance, "ETH")

                val currentBalance = localDataSource.loadWalletBalance(walletId) ?: WalletBalance(walletId, System.currentTimeMillis())

                val updatedBalance = currentBalance.copy(
                    ethereum = EthereumBalance(
                        address = coin.address,
                        wei = weiBalance,
                        eth = ethBalance.setScale(6, RoundingMode.HALF_UP).toPlainString(),
                        usdValue = usdValue
                    ),
                    lastUpdated = System.currentTimeMillis()
                )

                localDataSource.saveWalletBalance(updatedBalance)
                Log.d("WalletRepo", " Ethereum balance synced: $ethBalance ETH")
            }

            is com.example.nexuswallet.feature.coin.Result.Error -> {
                Log.e("WalletRepo", " Error syncing Ethereum: ${balanceResult.message}")
            }
            else -> {}
        }
    }

    private suspend fun syncSolanaBalance(walletId: String, coin: SolanaCoin) {
        Log.d("WalletRepo", "Syncing Solana balance for: ${coin.address}")

        val balanceResult = solanaBlockchainRepository.getBalance(coin.address)

        when (balanceResult) {
            is com.example.nexuswallet.feature.coin.Result.Success -> {
                val solBalance = balanceResult.data
                val lamportsBalance = (solBalance * BigDecimal("1000000000")).toBigInteger().toString()
                val usdValue = calculateUsdValue(solBalance, "SOL")

                val currentBalance = localDataSource.loadWalletBalance(walletId) ?: WalletBalance(walletId, System.currentTimeMillis())

                val updatedBalance = currentBalance.copy(
                    solana = SolanaBalance(
                        address = coin.address,
                        lamports = lamportsBalance,
                        sol = solBalance.setScale(9, RoundingMode.HALF_UP).toPlainString(),
                        usdValue = usdValue
                    ),
                    lastUpdated = System.currentTimeMillis()
                )

                localDataSource.saveWalletBalance(updatedBalance)
                Log.d("WalletRepo", " Solana balance synced: $solBalance SOL")
            }

            is com.example.nexuswallet.feature.coin.Result.Error -> {
                Log.e("WalletRepo", " Error syncing Solana: ${balanceResult.message}")
            }
            else -> {}
        }
    }

    private suspend fun syncUSDCBalance(walletId: String, coin: USDCCoin) {
        Log.d("WalletRepo", "Syncing USDC balance for: ${coin.address}")

        val result = usdcBlockchainRepository.getUSDCBalance(
            address = coin.address,
            network = coin.network
        )

        when (result) {
            is com.example.nexuswallet.feature.coin.Result.Success -> {
                val usdcBalance = result.data

                val currentBalance = localDataSource.loadWalletBalance(walletId)
                    ?: WalletBalance(walletId, System.currentTimeMillis())

                val updatedBalance = currentBalance.copy(
                    usdc = usdcBalance,
                    lastUpdated = System.currentTimeMillis()
                )

                localDataSource.saveWalletBalance(updatedBalance)
                Log.d("WalletRepo", " USDC balance synced: ${usdcBalance.amountDecimal} USDC")
            }

            is com.example.nexuswallet.feature.coin.Result.Error -> {
                Log.e("WalletRepo", " Error syncing USDC: ${result.message}")
            }
            else -> {}
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
        bitcoinNetwork: BitcoinNetwork = BitcoinNetwork.TESTNET
    ): Result<Wallet> {
        return try {
            val walletId = "wallet_${System.currentTimeMillis()}"

            val bitcoinCoin = if (includeBitcoin) createBitcoinCoin(mnemonic, bitcoinNetwork) else null
            val ethereumCoin = if (includeEthereum) createEthereumCoin(mnemonic, ethereumNetwork) else null
            val solanaCoin = if (includeSolana) createSolanaCoin() else null

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

            // Initial balance sync
            syncWalletBalances(wallet)

            Result.success(wallet)
        } catch (e: Exception) {
            Log.e("WalletRepo", "Failed to create wallet: ${e.message}", e)
            Result.failure(e)
        }
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

        // Determine derivation path based on network
        val derivationPath = when (network) {
            is EthereumNetwork.Mainnet -> "m/44'/60'/0'/0/0"
            is EthereumNetwork.Sepolia -> "m/44'/60'/0'/0/0"
            else -> "m/44'/60'/0'/0/0" // Default
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

    private fun createSolanaCoin(): SolanaCoin {
        val keypair = Keypair.generate()
        return SolanaCoin(
            address = keypair.publicKey.toString(),
            publicKey = keypair.publicKey.toString()
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
            val keypair = Keypair.generate() // TODO: derive from mnemonic
            val privateKeyHex = keypair.secret.joinToString("") { "%02x".format(it) }

            keyManager.storePrivateKey(
                walletId = wallet.id,
                privateKey = privateKeyHex,
                keyType = "SOLANA_PRIVATE_KEY"
            )
        }
    }

    // === WALLET CRUD ===
    suspend fun saveWallet(wallet: Wallet) {
        localDataSource.saveWallet(wallet)
        updateWalletsFlow(wallet)
        syncWalletBalances(wallet)
    }

    suspend fun getWallet(walletId: String): Wallet? {
        val wallet = localDataSource.loadWallet(walletId)
        wallet?.let {
            scope.launch {
                syncWalletBalances(it)
            }
        }
        return wallet
    }

    fun getAllWallets(): Flow<List<Wallet>> {
        return localDataSource.loadAllWallets()
    }

    suspend fun deleteWallet(walletId: String) {
        localDataSource.deleteWallet(walletId)
        removeWalletFromFlow(walletId)
    }

    // === BALANCE OPERATIONS ===
    suspend fun getWalletBalance(walletId: String): WalletBalance? {
        return localDataSource.loadWalletBalance(walletId)
    }

    suspend fun getBitcoinBalance(walletId: String): BitcoinBalance? {
        return localDataSource.loadWalletBalance(walletId)?.bitcoin
    }

    suspend fun getEthereumBalance(walletId: String): EthereumBalance? {
        return localDataSource.loadWalletBalance(walletId)?.ethereum
    }

    suspend fun getSolanaBalance(walletId: String): SolanaBalance? {
        return localDataSource.loadWalletBalance(walletId)?.solana
    }

    suspend fun getUSDCBalance(walletId: String): USDCBalance? {
        return localDataSource.loadWalletBalance(walletId)?.usdc
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
    private fun calculateUsdValue(amount: BigDecimal, symbol: String): Double {
        val price = when (symbol) {
            "BTC" -> 45000.0
            "ETH" -> 3000.0
            "SOL" -> 30.0
            else -> 1.0
        }
        return amount.toDouble() * price
    }

    private fun getUSDCContractAddress(network: EthereumNetwork): String {
        return network.usdcContractAddress
    }

    private fun updateWalletsFlow(wallet: Wallet) {
        val currentWallets = _walletsFlow.value.toMutableList()
        val existingIndex = currentWallets.indexOfFirst { it.id == wallet.id }

        if (existingIndex >= 0) {
            currentWallets[existingIndex] = wallet
        } else {
            currentWallets.add(wallet)
        }

        _walletsFlow.value = currentWallets
    }

    private fun removeWalletFromFlow(walletId: String) {
        val currentWallets = _walletsFlow.value.toMutableList()
        currentWallets.removeAll { it.id == walletId }
        _walletsFlow.value = currentWallets
    }
}