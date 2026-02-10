package com.example.nexuswallet.feature.wallet.data.repository


import android.util.Log
import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import com.example.nexuswallet.feature.wallet.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.USDCWallet
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.WalletType
import com.example.nexuswallet.feature.wallet.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.wallet.usdc.USDCBlockchainRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.security.SecureRandom
import javax.inject.Inject
import org.bitcoinj.wallet.Wallet as BitcoinJWallet

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

    private val _walletsFlow = MutableStateFlow<List<CryptoWallet>>(emptyList())
    val walletsFlow: StateFlow<List<CryptoWallet>> = _walletsFlow

    init {
        scope.launch {
            localDataSource.loadAllWallets().collect { wallets ->
                _walletsFlow.value = wallets

                // Auto-sync balances when wallets are loaded
                wallets.forEach { wallet ->
                    syncWalletBalance(wallet)
                }
            }
        }
    }

    suspend fun syncWalletBalance(wallet: CryptoWallet) {
        Log.d("WalletRepo", "=== SYNC WALLET BALANCE ===")
        Log.d("WalletRepo", "Wallet ID: ${wallet.id}")
        Log.d("WalletRepo", "Wallet Type: ${wallet.walletType}")

        when (wallet.walletType) {
            WalletType.BITCOIN -> {
                if (wallet is BitcoinWallet) syncBitcoinBalance(wallet)
            }
            WalletType.ETHEREUM, WalletType.ETHEREUM_SEPOLIA -> {
                if (wallet is EthereumWallet) syncEthereumBalance(wallet)
            }
            WalletType.USDC -> {
                if (wallet is USDCWallet) {
                    syncUSDCBalance(wallet)
                }
            }
            WalletType.SOLANA -> {
                if (wallet is SolanaWallet) syncSolanaBalance(wallet)
            }
            WalletType.MULTICHAIN -> {
                if (wallet is MultiChainWallet) {
                    wallet.ethereumWallet?.let { syncEthereumBalance(it) }
                    wallet.polygonWallet?.let { syncEthereumBalance(it) }
                    wallet.bscWallet?.let { syncEthereumBalance(it) }
                    wallet.bitcoinWallet?.let { syncBitcoinBalance(it) }
                    Log.d("WalletRepo", "Multi-chain wallet synced")
                }
            }
            else -> {
                Log.d("WalletRepo", "Unknown wallet type: ${wallet.walletType}")
            }
        }
    }

    private suspend fun syncSolanaBalance(wallet: SolanaWallet) {
        try {
            Log.d("WalletRepo", "Syncing REAL Solana wallet balance...")
            Log.d("WalletRepo", "Address: ${wallet.address}")

            // Fetch balance from Solana blockchain
            val solBalance = solanaBlockchainRepository.getBalance(wallet.address)

            Log.d("WalletRepo", "Real balance fetched: $solBalance SOL")

            val usdValue = calculateUsdValue(solBalance, "SOL")
            Log.d("WalletRepo", "USD Value: $$usdValue")

            // Convert SOL to lamports for storage (1 SOL = 1,000,000,000 lamports)
            val lamportsBalance = (solBalance * BigDecimal("1000000000")).toPlainString()

            val balance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = lamportsBalance, // Store in lamports
                nativeBalanceDecimal = solBalance.setScale(9, RoundingMode.HALF_UP).toPlainString(), // SOL with 9 decimals
                usdValue = usdValue,
                tokens = emptyList(),
                lastUpdated = System.currentTimeMillis()
            )

            saveWalletBalance(balance)
            Log.d("WalletRepo", " Solana balance synced successfully: $solBalance SOL")

        } catch (e: Exception) {
            Log.e("WalletRepo", " Error syncing Solana balance: ${e.message}")

            // Fallback: Show 0 balance instead of fake 5.75
            val zeroBalance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = "0", // 0 lamports
                nativeBalanceDecimal = "0", // 0 SOL
                usdValue = 0.0,
                tokens = emptyList(),
                lastUpdated = System.currentTimeMillis()
            )

            saveWalletBalance(zeroBalance)
            Log.d("WalletRepo", "⚠ Using zero balance due to error")
        }
    }

    private suspend fun syncEthereumBalance(wallet: EthereumWallet) {
        try {
            Log.d("WalletRepo", "Syncing Ethereum wallet balance...")
            Log.d("WalletRepo", "Address: ${wallet.address}")
            Log.d("WalletRepo", "Network: ${wallet.network}")

            // Get balance from blockchain
            val ethBalance = ethereumBlockchainRepository.getEthereumBalance(
                address = wallet.address,
                network = wallet.network
            )

            Log.d("WalletRepo", "Got balance from API: $ethBalance ETH")

            // Calculate USD value
            val ethPrice = when (wallet.network) {
                EthereumNetwork.SEPOLIA -> 3000.0 // Testnet ETH has no real value, use mainnet price
                else -> 3000.0 // Mainnet ETH price approximation
            }
            val usdValue = ethBalance.toDouble() * ethPrice

            // Convert ETH to wei for storage
            val weiBalance = (ethBalance * BigDecimal("1000000000000000000")).toPlainString()

            val balance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = weiBalance,
                nativeBalanceDecimal = ethBalance.toPlainString(),
                usdValue = usdValue,
                tokens = emptyList(),
                lastUpdated = System.currentTimeMillis()
            )

            // Save to local storage
            saveWalletBalance(balance)
            Log.d("WalletRepo", " Ethereum balance synced: $ethBalance ETH")

        } catch (e: Exception) {
            Log.e("WalletRepo", "Error syncing Ethereum balance: ${e.message}", e)

            // Fallback to sample data
            val fallbackBalance = createEthereumSampleBalance(wallet.id, wallet.address)
            saveWalletBalance(fallbackBalance)
            Log.d("WalletRepo", "⚠ Using sample balance due to error")
        }
    }

    private suspend fun syncBitcoinBalance(wallet: BitcoinWallet) {
        try {
            Log.d("WalletRepo", "Syncing REAL Bitcoin wallet balance...")
            Log.d("WalletRepo", "Address: ${wallet.address}")
            Log.d("WalletRepo", "Network: ${wallet.network}")

            val btcBalance = bitcoinBlockchainRepository.getBalance(
                address = wallet.address,
                network = wallet.network
            )

            Log.d("WalletRepo", "Real balance fetched: $btcBalance BTC")

            val usdValue = calculateUsdValue(btcBalance, "BTC")
            Log.d("WalletRepo", "USD Value: $$usdValue")

            // Convert BTC to satoshis for storage (1 BTC = 100,000,000 satoshis)
            val satoshiBalance = (btcBalance * BigDecimal("100000000")).toPlainString()

            val balance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = satoshiBalance,
                nativeBalanceDecimal = btcBalance.setScale(8, RoundingMode.HALF_UP).toPlainString(),
                usdValue = usdValue,
                tokens = emptyList(),
                lastUpdated = System.currentTimeMillis()
            )

            saveWalletBalance(balance)
            Log.d("WalletRepo", " Bitcoin balance synced successfully: $btcBalance BTC")

        } catch (e: Exception) {
            Log.e("WalletRepo", " Error syncing Bitcoin balance: ${e.message}")

            val zeroBalance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = "0",
                nativeBalanceDecimal = "0",
                usdValue = 0.0,
                tokens = emptyList(),
                lastUpdated = System.currentTimeMillis()
            )

            saveWalletBalance(zeroBalance)
            Log.d("WalletRepo", "⚠ Using zero balance due to error")
        }
    }

    private suspend fun syncUSDCBalance(wallet: USDCWallet) {
        try {
            Log.d("WalletRepo", "Syncing USDC wallet balance...")
            Log.d("WalletRepo", "Address: ${wallet.address}")
            Log.d("WalletRepo", "Network: ${wallet.network}")
            Log.d("WalletRepo", "Contract: ${wallet.contractAddress}")

            // Get USDC balance from USDC repository
            val usdcBalance = usdcBlockchainRepository.getUSDCBalance(
                address = wallet.address,
                network = wallet.network
            )

            Log.d("WalletRepo", "Got USDC balance: ${usdcBalance.balanceDecimal} USDC")

            // Create WalletBalance with USDC as native balance
            val balance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = usdcBalance.balance, // USDC units (6 decimals)
                nativeBalanceDecimal = usdcBalance.balanceDecimal, // Human readable USDC
                usdValue = usdcBalance.usdValue, // USD value of USDC
                tokens = emptyList(),
                lastUpdated = System.currentTimeMillis()
            )

            saveWalletBalance(balance)
            Log.d("WalletRepo", " USDC balance synced: ${usdcBalance.balanceDecimal} USDC")

        } catch (e: Exception) {
            Log.e("WalletRepo", "Error syncing USDC balance: ${e.message}", e)

            // Fallback to zero balance
            val zeroBalance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = "0",
                nativeBalanceDecimal = "0",
                usdValue = 0.0,
                tokens = emptyList(),
                lastUpdated = System.currentTimeMillis()
            )

            saveWalletBalance(zeroBalance)
            Log.d("WalletRepo", "⚠ Using zero balance due to error")
        }
    }

    private fun createEthereumSampleBalance(
        walletId: String,
        address: String,
    ): WalletBalance {
        // Generate a "realistic" sample balance based on address hash
        val hash = address.hashCode().toLong() and 0xFFFFFFFFL
        val ethAmount = (hash % 50L + 1L).toDouble() / 10.0 // 0.1 to 5.0 ETH
        val ethBalance = BigDecimal.valueOf(ethAmount)

        val weiBalance = (ethBalance * BigDecimal("1000000000000000000")).toPlainString()
        val usdValue = ethAmount * 3000.0 // Approx $3000 per ETH

        return WalletBalance(
            walletId = walletId,
            address = address,
            nativeBalance = weiBalance,
            nativeBalanceDecimal = ethBalance.toPlainString(),
            usdValue = usdValue,
            tokens = emptyList(),
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun createBitcoinSampleBalance(walletId: String, address: String): WalletBalance {
        val hash = address.hashCode().toLong() and 0xFFFFFFFFL
        val btcAmount = (hash % 2L + 1L).toDouble() / 10.0 // 0.1 to 0.2 BTC
        val satoshiBalance = (BigDecimal.valueOf(btcAmount) * BigDecimal("100000000")).toPlainString()
        val usdValue = btcAmount * 45000.0 // Approx $45,000 per BTC

        return WalletBalance(
            walletId = walletId,
            address = address,
            nativeBalance = satoshiBalance,
            nativeBalanceDecimal = btcAmount.toString(),
            usdValue = usdValue,
            tokens = emptyList(),
            lastUpdated = System.currentTimeMillis()
        )
    }


    // === WALLET CRUD OPERATIONS ===
    suspend fun saveWallet(wallet: CryptoWallet) {
        localDataSource.saveWallet(wallet)
        updateWalletsFlow(wallet)

        // Auto-sync balance after saving
        syncWalletBalance(wallet)
    }

    // === UPDATE loadWallet to trigger sync ===
    suspend fun getWallet(walletId: String): CryptoWallet? {
        val wallet = localDataSource.loadWallet(walletId)
        wallet?.let {
            // Trigger background sync
            scope.launch {
                syncWalletBalance(it)
            }

            // If it's USDC wallet, also sync parent ETH balance for gas estimates
            if (it is USDCWallet && it.parentEthereumWalletId != null) {
                val ethWallet = localDataSource.loadWallet(it.parentEthereumWalletId)
                if (ethWallet is EthereumWallet) {
                    syncEthereumBalance(ethWallet)
                }
            }
        }
        return wallet
    }

    suspend fun refreshWalletBalances() {
        Log.d("WalletRepo", "=== REFRESHING ALL WALLET BALANCES ===")

        val wallets = _walletsFlow.value
        wallets.forEach { wallet ->
            syncWalletBalance(wallet)
            delay(1000) // Rate limiting between syncs
        }

        Log.d("WalletRepo", " All wallet balances refreshed")
    }

    fun getAllWallets(): Flow<List<CryptoWallet>> {
        return localDataSource.loadAllWallets()
    }

    suspend fun deleteWallet(walletId: String) {
        localDataSource.deleteWallet(walletId)
        removeWalletFromFlow(walletId)
    }

    // === WALLET CREATION & DERIVATION ===
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
        network: BitcoinNetwork = BitcoinNetwork.TESTNET
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

        // Get private key in WIF format
        val privateKeyWIF = key.getPrivateKeyEncoded(params).toString()

        val bitcoinWallet = BitcoinWallet(
            id = "btc_${System.currentTimeMillis()}",
            name = name,
            address = address,
            publicKey = key.pubKey.toString(),
            privateKeyEncrypted = privateKeyWIF,
            network = network,
            derivationPath = "m/44'/0'/0'/0/0",
            xpub = xpub,
            mnemonicHash = mnemonic.hashCode().toString(),
            createdAt = System.currentTimeMillis(),
            isBackedUp = false,
            walletType = WalletType.BITCOIN
        )

        // Secure mnemonic AND private key in background
        scope.launch {
            // Store mnemonic
            securityManager.secureMnemonic(bitcoinWallet.id, mnemonic)

            // Store Bitcoin private key with correct key type
            keyManager.storePrivateKey(
                walletId = bitcoinWallet.id,
                privateKey = privateKeyWIF,
                keyType = "BTC_PRIVATE_KEY"
            )
        }

        return bitcoinWallet
    }

    suspend fun createEthereumWallet(
        mnemonic: List<String>,
        name: String,
        network: EthereumNetwork = EthereumNetwork.MAINNET
    ): Result<EthereumWallet> {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")

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

            val privateKey = derivedKey.privateKey
            val privateKeyHex = "0x${privateKey.toString(16)}"

            Log.d(
                "WalletRepo",
                "Generated private key (first 10 chars): ${privateKeyHex.take(10)}..."
            )

            // Create credentials from the derived key
            val credentials = Credentials.create(derivedKey)
            Log.d("WalletRepo", "Generated address: ${credentials.address}")

            val wallet = EthereumWallet(
                id = "eth_${System.currentTimeMillis()}",
                name = name,
                address = credentials.address,
                publicKey = derivedKey.publicKeyPoint.getEncoded(false)
                    .joinToString("") { "%02x".format(it) },
                privateKeyEncrypted = "",
                network = network,
                derivationPath = derivationPath,
                isSmartContractWallet = false,
                walletFile = null,
                mnemonicHash = mnemonic.hashCode().toString(),
                createdAt = System.currentTimeMillis(),
                isBackedUp = false,
                walletType = if (network == EthereumNetwork.SEPOLIA) WalletType.ETHEREUM_SEPOLIA else WalletType.ETHEREUM
            )

            // Store the wallet first
            saveWallet(wallet)

            // Store the private key using KeyManager
            val storeResult = keyManager.storePrivateKey(
                walletId = wallet.id,
                privateKey = privateKeyHex,
                keyType = "ETH_PRIVATE_KEY"
            )

            if (storeResult.isFailure) {
                Log.e(
                    "WalletRepo",
                    "Failed to store private key: ${storeResult.exceptionOrNull()?.message}"
                )
            } else {
                Log.d("WalletRepo", "Private key stored successfully")
            }

            // Store mnemonic securely
            securityManager.secureMnemonic(wallet.id, mnemonic)

            Result.success(wallet)

        } catch (e: Exception) {
            Log.e("WalletRepo", "Failed to create Ethereum wallet: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createUSDCWallet(
        mnemonic: List<String>,
        name: String,
        network: EthereumNetwork = EthereumNetwork.SEPOLIA
    ): Result<USDCWallet> {
        return try {
            Log.d("WalletRepo", "Creating USDC wallet...")

            // Get USDC contract address for the network
            val contractAddress = getUSDCContractAddress(network)
                ?: return Result.failure(IllegalArgumentException("USDC not supported on $network"))

            // Generate Ethereum-like credentials for the address
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")

            // Use Ethereum derivation path since USDC is an ERC-20 token on Ethereum
            val derivationPath = when (network) {
                EthereumNetwork.SEPOLIA -> "m/44'/60'/0'/0/0"  // Ethereum testnet path
                else -> "m/44'/60'/0'/0/0"  // Default Ethereum path
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

            // Create credentials from the derived key
            val credentials = Credentials.create(derivedKey)

            val privateKeyHex = "0x${derivedKey.privateKey.toString(16)}"

            Log.d("WalletRepo", "Generated USDC wallet address: ${credentials.address}")

            val usdcWallet = USDCWallet(
                id = "usdc_${System.currentTimeMillis()}",
                name = name,
                address = credentials.address,
                publicKey = derivedKey.publicKeyPoint.getEncoded(false)
                    .joinToString("") { "%02x".format(it) },
                privateKeyEncrypted = "",
                network = network,
                contractAddress = contractAddress,
                parentEthereumWalletId = null,
                mnemonicHash = mnemonic.hashCode().toString(),
                createdAt = System.currentTimeMillis(),
                isBackedUp = false,
                walletType = WalletType.USDC
            )

            // Save the wallet first
            saveWallet(usdcWallet)

            // Store the private key using KeyManager
            val storeResult = keyManager.storePrivateKey(
                walletId = usdcWallet.id,
                privateKey = privateKeyHex,
                keyType = "ETH_PRIVATE_KEY"  // Same as Ethereum since USDC is ERC-20
            )

            if (storeResult.isFailure) {
                Log.e("WalletRepo", "Failed to store private key: ${storeResult.exceptionOrNull()?.message}")
            } else {
                Log.d("WalletRepo", "Private key stored successfully")
            }

            // Store mnemonic securely
            securityManager.secureMnemonic(usdcWallet.id, mnemonic)

            Log.d("WalletRepo", " USDC wallet created: ${usdcWallet.address}")
            Result.success(usdcWallet)

        } catch (e: Exception) {
            Log.e("WalletRepo", "Failed to create USDC wallet: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createMultiChainWallet(
        mnemonic: List<String>,
        name: String
    ): Result<MultiChainWallet> {
        return try {
            // Create Bitcoin wallet
            val bitcoinWallet = createBitcoinWallet(mnemonic, "$name (Bitcoin)")

            // Create Ethereum wallet
            val ethereumResult = createEthereumWallet(mnemonic, "$name (Ethereum)")
            if (ethereumResult.isFailure) {
                return Result.failure(
                    ethereumResult.exceptionOrNull()
                        ?: IllegalStateException("Failed to create Ethereum wallet")
                )
            }
            val ethereumWallet = ethereumResult.getOrThrow()

            // Create Polygon wallet
            val polygonResult = createEthereumWallet(mnemonic, "$name (Polygon)")
            if (polygonResult.isFailure) {
                return Result.failure(
                    polygonResult.exceptionOrNull()
                        ?: IllegalStateException("Failed to create Polygon wallet")
                )
            }
            val polygonWalletRaw = polygonResult.getOrThrow()

            // Create BSC wallet
            val bscResult = createEthereumWallet(mnemonic, "$name (BSC)")
            if (bscResult.isFailure) {
                return Result.failure(
                    bscResult.exceptionOrNull()
                        ?: IllegalStateException("Failed to create BSC wallet")
                )
            }
            val bscWalletRaw = bscResult.getOrThrow()

            // Create wallet instances with correct networks
            val polygonWallet = EthereumWallet(
                id = polygonWalletRaw.id,
                name = "$name (Polygon)",
                address = polygonWalletRaw.address,
                publicKey = polygonWalletRaw.publicKey,
                privateKeyEncrypted = polygonWalletRaw.privateKeyEncrypted,
                network = EthereumNetwork.POLYGON,
                derivationPath = polygonWalletRaw.derivationPath,
                isSmartContractWallet = polygonWalletRaw.isSmartContractWallet,
                walletFile = polygonWalletRaw.walletFile,
                mnemonicHash = polygonWalletRaw.mnemonicHash,
                createdAt = polygonWalletRaw.createdAt,
                isBackedUp = polygonWalletRaw.isBackedUp,
                walletType = polygonWalletRaw.walletType
            )

            val bscWallet = EthereumWallet(
                id = bscWalletRaw.id,
                name = "$name (BSC)",
                address = bscWalletRaw.address,
                publicKey = bscWalletRaw.publicKey,
                privateKeyEncrypted = bscWalletRaw.privateKeyEncrypted,
                network = EthereumNetwork.BSC,
                derivationPath = bscWalletRaw.derivationPath,
                isSmartContractWallet = bscWalletRaw.isSmartContractWallet,
                walletFile = bscWalletRaw.walletFile,
                mnemonicHash = bscWalletRaw.mnemonicHash,
                createdAt = bscWalletRaw.createdAt,
                isBackedUp = bscWalletRaw.isBackedUp,
                walletType = bscWalletRaw.walletType
            )

            // Store the chain wallets
            saveWallet(polygonWallet)
            saveWallet(bscWallet)

            // Use Ethereum address as primary
            val primaryAddress = ethereumWallet.address

            val multiChainWallet = MultiChainWallet(
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

            // Store the multi-chain wallet
            saveWallet(multiChainWallet)

            Result.success(multiChainWallet)

        } catch (e: Exception) {
            Log.e("WalletRepo", "Failed to create multi-chain wallet: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createSolanaWallet(
        mnemonic: List<String>,
        name: String
    ): Result<SolanaWallet> {
        return try {
            Log.d("WalletRepo", "Creating Solana wallet...")

            // Generate keypair
            val keypair = Keypair.generate()

            // Get the address
            val address = keypair.publicKey.toString()

            val wallet = SolanaWallet(
                id = "sol_${System.currentTimeMillis()}",
                name = name,
                address = address,
                publicKey = keypair.publicKey.toString(),
                privateKeyEncrypted = "",
                mnemonicHash = mnemonic.hashCode().toString(),
                createdAt = System.currentTimeMillis(),
                isBackedUp = false,
                walletType = WalletType.SOLANA
            )

            // Save the wallet first
            saveWallet(wallet)

            // Convert private key to hex (64 bytes = 128 hex chars)
            val privateKeyBytes = keypair.secret
            val privateKeyHex = privateKeyBytes.joinToString("") { "%02x".format(it) }

            Log.d("WalletRepo", "Solana private key length: ${privateKeyHex.length} hex chars")

            val storeResult = keyManager.storePrivateKey(
                walletId = wallet.id,
                privateKey = privateKeyHex,
                keyType = "SOLANA_PRIVATE_KEY"
            )

            if (storeResult.isFailure) {
                Log.e("WalletRepo", "Failed to store Solana private key: ${storeResult.exceptionOrNull()?.message}")
                // Still return the wallet - private key storage failed but wallet exists
            } else {
                Log.d("WalletRepo", "Solana private key stored successfully")
            }

            // Store mnemonic securely
            securityManager.secureMnemonic(wallet.id, mnemonic)

            Log.d("WalletRepo", "Solana wallet created: $address")
            Result.success(wallet)

        } catch (e: Exception) {
            Log.e("WalletRepo", "Failed to create Solana wallet: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun getUSDCContractAddress(network: EthereumNetwork): String? {
        return when (network) {
            EthereumNetwork.MAINNET -> "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
            EthereumNetwork.SEPOLIA -> "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
            EthereumNetwork.POLYGON -> "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174"
            EthereumNetwork.BSC -> "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d"
            else -> null
        }
    }

    // === BALANCE OPERATIONS ===
    suspend fun saveWalletBalance(balance: WalletBalance) {
        localDataSource.saveWalletBalance(balance)
    }

    suspend fun getWalletBalance(walletId: String): WalletBalance? {
        return localDataSource.loadWalletBalance(walletId)
    }

    fun createSampleBalance(walletId: String, address: String): WalletBalance {
        val nativeBalance = when {
            address.startsWith("bc1") -> "150000000" // 1.5 BTC in satoshis
            address.startsWith("0x") -> "2500000000000000000" // 2.5 ETH in wei
            else -> "0"
        }

        val nativeDecimal = formatBalance(nativeBalance, 18)
        val usdValue = if (address.startsWith("bc1")) 45000.0 else 8750.0

        return WalletBalance(
            walletId = walletId,
            address = address,
            nativeBalance = nativeBalance,
            nativeBalanceDecimal = nativeDecimal,
            usdValue = usdValue,
            tokens = emptyList()
        )
    }


    private suspend fun syncEthereumWallet(wallet: EthereumWallet) {
        try {
            Log.d("WalletRepo", " Syncing Ethereum wallet:")
            Log.d("WalletRepo", "   - Wallet ID: ${wallet.id}")
            Log.d("WalletRepo", "   - Address: ${wallet.address}")
            Log.d("WalletRepo", "   - Network: ${wallet.network}")

            // Get balance from blockchain repository
            val ethBalance = ethereumBlockchainRepository.getEthereumBalance(
                address = wallet.address,
                network = wallet.network
            )

            Log.d("WalletRepo", " Balance received: $ethBalance ETH")

            // Calculate USD value
            val usdValue = calculateUsdValue(ethBalance, "ETH")
            Log.d("WalletRepo", "   - USD Value: $$usdValue")

            // Convert ETH to wei for storage
            val weiBalance = (ethBalance * BigDecimal("1000000000000000000")).toPlainString()

            // Create updated balance
            val balance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = weiBalance,
                nativeBalanceDecimal = ethBalance.toPlainString(),
                usdValue = usdValue,
                tokens = emptyList(),
                lastUpdated = System.currentTimeMillis()
            )

            Log.d("WalletRepo", " Saving wallet balance")

            // Save balance
            saveWalletBalance(balance)
            Log.d("WalletRepo", " Ethereum wallet synced successfully")

        } catch (e: Exception) {
            Log.e("WalletRepo", " Error syncing Ethereum wallet: ${e.message}", e)

            // Fallback to sample data on error
            val fallbackBalance = createSampleBalance(wallet.id, wallet.address)
            saveWalletBalance(fallbackBalance)
            Log.d("WalletRepo", "️ Using fallback sample balance due to error")
        }
    }

    // === TRANSACTION OPERATIONS ===
    suspend fun saveTransactions(walletId: String, transactions: List<Transaction>) {
        localDataSource.saveTransactions(walletId, transactions)
    }

    fun getTransactions(walletId: String): Flow<List<Transaction>> {
        return localDataSource.loadTransactions(walletId)
    }

    // === FORMATTING HELPERS ===
    fun formatBalance(balanceStr: String, decimals: Int): String {
        return try {
            val bigInt = BigInteger(balanceStr)
            val divisor = BigInteger.TEN.pow(decimals)
            val integerPart = bigInt.divide(divisor)
            val fractionalPart = bigInt.mod(divisor)

            if (fractionalPart == BigInteger.ZERO) {
                integerPart.toString()
            } else {
                val fractionalStr = fractionalPart.toString().padStart(decimals, '0')
                    .trimEnd('0')
                "$integerPart.$fractionalStr"
            }
        } catch (e: Exception) {
            "0"
        }
    }

    private fun calculateUsdValue(amount: BigDecimal, symbol: String): Double {
        // For demo: use fixed prices
        val price = when (symbol) {
            "BTC" -> 45000.0
            "ETH" -> 3000.0
            "SOL" -> 30.0
            else -> 1.0
        }
        return (amount.toDouble() * price)
    }

    private fun updateWalletsFlow(wallet: CryptoWallet) {
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