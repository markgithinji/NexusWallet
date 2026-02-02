package com.example.nexuswallet.feature.wallet.data.repository


import android.util.Log
import androidx.room.util.copy
import com.example.nexuswallet.feature.authentication.domain.BackupResult
import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.data.remote.ChainId
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.WalletSettings
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.crypto.ChildNumber.HARDENED_BIT
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.wallet.DeterministicSeed
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.security.SecureRandom
import javax.inject.Inject
import org.bitcoinj.wallet.Wallet as BitcoinJWallet

class WalletRepository @Inject constructor(
    private val localDataSource: WalletLocalDataSource,
    private val securityManager: SecurityManager,
    private val blockchainRepository: BlockchainRepository,
    private val keyManager: KeyManager
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _walletsFlow = MutableStateFlow<List<CryptoWallet>>(emptyList())
    val walletsFlow: StateFlow<List<CryptoWallet>> = _walletsFlow

    init {
        scope.launch {
            localDataSource.loadAllWallets().collect { wallets ->
                _walletsFlow.value = wallets
            }
        }
    }

    // === WALLET CRUD OPERATIONS ===
    suspend fun saveWallet(wallet: CryptoWallet) {
        localDataSource.saveWallet(wallet)
        updateWalletsFlow(wallet)

        // Create initial sample balance
        val sampleBalance = createSampleBalance(wallet.id, wallet.address)
        saveWalletBalance(sampleBalance)
    }

    suspend fun getWallet(walletId: String): CryptoWallet? {
        return localDataSource.loadWallet(walletId)
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
        network: BitcoinNetwork = BitcoinNetwork.MAINNET
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

        val bitcoinWallet = BitcoinWallet(
            id = "btc_${System.currentTimeMillis()}",
            name = name,
            address = address,
            publicKey = key.pubKey.toString(),
            privateKeyEncrypted = "",
            network = network,
            derivationPath = "m/44'/0'/0'/0/0",
            xpub = xpub,
            mnemonicHash = mnemonic.hashCode().toString(),
            createdAt = System.currentTimeMillis(),
            isBackedUp = false,
            walletType = WalletType.BITCOIN
        )

        // Secure the mnemonic in background
        scope.launch {
            securityManager.secureMnemonic(bitcoinWallet.id, mnemonic)
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

            Log.d("WalletRepo", "Generated private key (first 10 chars): ${privateKeyHex.take(10)}...")

            // Create credentials from the derived key
            val credentials = Credentials.create(derivedKey)
            Log.d("WalletRepo", "Generated address: ${credentials.address}")

            val wallet = EthereumWallet(
                id = "eth_${System.currentTimeMillis()}",
                name = name,
                address = credentials.address,
                publicKey = derivedKey.publicKeyPoint.getEncoded(false).joinToString("") { "%02x".format(it) },
                privateKeyEncrypted = "",
                network = network,  // Use the network parameter
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

            // Store the REAL private key using KeyManager
            val storeResult = keyManager.storePrivateKey(
                walletId = wallet.id,
                privateKey = privateKeyHex,
                keyType = "ETH_PRIVATE_KEY"
            )

            if (storeResult.isFailure) {
                Log.e("WalletRepo", "Failed to store private key: ${storeResult.exceptionOrNull()?.message}")
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

    suspend fun createMultiChainWallet(
        mnemonic: List<String>,
        name: String
    ): Result<MultiChainWallet> {
        return try {
            // Create Bitcoin wallet
            val bitcoinWallet = createBitcoinWallet(mnemonic, "$name (Bitcoin)")

            // Create Ethereum wallet - handle Result type
            val ethereumResult = createEthereumWallet(mnemonic, "$name (Ethereum)")
            if (ethereumResult.isFailure) {
                return Result.failure(
                    ethereumResult.exceptionOrNull() ?:
                    IllegalStateException("Failed to create Ethereum wallet")
                )
            }
            val ethereumWallet = ethereumResult.getOrThrow()

            // Create Polygon wallet
            val polygonResult = createEthereumWallet(mnemonic, "$name (Polygon)")
            if (polygonResult.isFailure) {
                return Result.failure(
                    polygonResult.exceptionOrNull() ?:
                    IllegalStateException("Failed to create Polygon wallet")
                )
            }
            val polygonWalletRaw = polygonResult.getOrThrow()

            // Create BSC wallet
            val bscResult = createEthereumWallet(mnemonic, "$name (BSC)")
            if (bscResult.isFailure) {
                return Result.failure(
                    bscResult.exceptionOrNull() ?:
                    IllegalStateException("Failed to create BSC wallet")
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

    // === SYNC WITH BLOCKCHAIN ===
    suspend fun syncWalletWithBlockchain(walletId: String) {
        val wallet = getWallet(walletId) ?: return

        Log.d("WalletRepo", "Syncing wallet $walletId with blockchain...")

        when (wallet) {
            is EthereumWallet -> {
                syncEthereumWallet(wallet)
            }

            is BitcoinWallet -> {
                syncBitcoinWallet(wallet)
            }

            is MultiChainWallet -> {
                syncMultiChainWallet(wallet)
            }

            is SolanaWallet -> {
                syncSolanaWallet(wallet)
            }
        }
    }

    private suspend fun syncEthereumWallet(wallet: EthereumWallet) {
        try {
            Log.d("WalletRepo", " Syncing Ethereum wallet:")
            Log.d("WalletRepo", "   - Wallet ID: ${wallet.id}")
            Log.d("WalletRepo", "   - Address: ${wallet.address}")
            Log.d("WalletRepo", "   - Network: ${wallet.network}")

            // Get balance from blockchain repository
            val ethBalance = blockchainRepository.getEthereumBalance(
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

    private suspend fun syncBitcoinWallet(wallet: BitcoinWallet) {
        try {
            // Get real balance from blockchain
            val btcBalance = blockchainRepository.getBitcoinBalance(wallet.address)
            Log.d("WalletRepo", "BTC Balance for ${wallet.address}: $btcBalance")

            // Create updated balance
            val balance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = (btcBalance * BigDecimal("100000000")).toPlainString(),
                nativeBalanceDecimal = btcBalance.toPlainString(),
                usdValue = calculateUsdValue(btcBalance, "BTC"),
                tokens = emptyList()
            )

            // Save balance
            saveWalletBalance(balance)

            Log.d("WalletRepo", "Bitcoin wallet synced successfully")

        } catch (e: Exception) {
            Log.e("WalletRepo", "Error syncing Bitcoin wallet: ${e.message}")
            // Fallback to sample data
            val fallbackBalance = createSampleBalance(wallet.id, wallet.address)
            saveWalletBalance(fallbackBalance)
        }
    }

    private suspend fun syncMultiChainWallet(wallet: MultiChainWallet) {
        val allBalances = mutableListOf<WalletBalance>()
        var totalUsdValue = 0.0

        // Sync Bitcoin if exists
        wallet.bitcoinWallet?.let { btcWallet ->
            try {
                val btcBalance = blockchainRepository.getBitcoinBalance(btcWallet.address)
                val balance = WalletBalance(
                    walletId = wallet.id,
                    address = btcWallet.address,
                    nativeBalance = (btcBalance * BigDecimal("100000000")).toPlainString(),
                    nativeBalanceDecimal = btcBalance.toPlainString(),
                    usdValue = calculateUsdValue(btcBalance, "BTC"),
                    tokens = emptyList()
                )
                allBalances.add(balance)
                totalUsdValue += balance.usdValue
            } catch (e: Exception) {
                Log.e("WalletRepo", "Error syncing BTC in multi-chain: ${e.message}")
            }
        }

        // Sync Ethereum if exists
        wallet.ethereumWallet?.let { ethWallet ->
            try {
                val ethBalance = blockchainRepository.getEthereumBalance(
                    ethWallet.address,
                    ethWallet.network
                )

                val tokens = blockchainRepository.getTokenBalances(
                    ethWallet.address,
                    ChainId.ETHEREUM_MAINNET
                )

                val balance = WalletBalance(
                    walletId = wallet.id,
                    address = ethWallet.address,
                    nativeBalance = (ethBalance * BigDecimal("1000000000000000000")).toPlainString(),
                    nativeBalanceDecimal = ethBalance.toPlainString(),
                    usdValue = calculateUsdValue(ethBalance, "ETH"),
                    tokens = tokens
                )
                allBalances.add(balance)
                totalUsdValue += balance.usdValue
            } catch (e: Exception) {
                Log.e("WalletRepo", "Error syncing ETH in multi-chain: ${e.message}")
            }
        }
        // Save combined balance
        val combinedBalance = WalletBalance(
            walletId = wallet.id,
            address = wallet.address,
            nativeBalance = allBalances.joinToString("|") { it.nativeBalance },
            nativeBalanceDecimal = allBalances.joinToString(" + ") { it.nativeBalanceDecimal },
            usdValue = totalUsdValue,
            tokens = allBalances.flatMap { it.tokens }
        )

        saveWalletBalance(combinedBalance)
        Log.d("WalletRepo", "Multi-chain wallet synced successfully")
    }

    private suspend fun syncSolanaWallet(wallet: SolanaWallet) {
        try {
            Log.d("WalletRepo", "Solana wallet sync not implemented yet, using demo data")

            val solBalance = BigDecimal("5.75")
            val tokens = listOf(
                TokenBalance(
                    tokenId = "demo_sol_usdc",
                    symbol = "USDC",
                    name = "USD Coin (Solana)",
                    contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                    balance = "1000000000", // 1000 USDC
                    balanceDecimal = "1000",
                    usdPrice = 1.0,
                    usdValue = 1000.0,
                    decimals = 6,
                    chain = ChainType.SOLANA
                )
            )

            val balance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = (solBalance * BigDecimal("1000000000")).toPlainString(), // SOL has 9 decimals
                nativeBalanceDecimal = solBalance.toPlainString(),
                usdValue = calculateUsdValue(solBalance, "SOL"),
                tokens = tokens
            )

            saveWalletBalance(balance)

            Log.d("WalletRepo", "Solana wallet synced with demo data")

        } catch (e: Exception) {
            Log.e("WalletRepo", "Error syncing Solana wallet: ${e.message}")
            val fallbackBalance = createSampleBalance(wallet.id, wallet.address)
            saveWalletBalance(fallbackBalance)
        }
    }

    suspend fun refreshAllWallets() {
        val wallets = _walletsFlow.value
        wallets.forEach { wallet ->
            syncWalletWithBlockchain(wallet.id)
        }
    }

    // === TRANSACTION OPERATIONS ===
    suspend fun saveTransactions(walletId: String, transactions: List<Transaction>) {
        localDataSource.saveTransactions(walletId, transactions)
    }

    fun getTransactions(walletId: String): Flow<List<Transaction>> {
        return localDataSource.loadTransactions(walletId)
    }

    // === SETTINGS OPERATIONS ===
    suspend fun saveSettings(settings: WalletSettings) {
        localDataSource.saveSettings(settings)
    }

    suspend fun getSettings(walletId: String): WalletSettings? {
        return localDataSource.loadSettings(walletId)
    }

    // === MNEMONIC & SECURITY OPERATIONS ===
    suspend fun getMnemonic(walletId: String): List<String>? {
        return securityManager.retrieveMnemonic(walletId)
    }

    suspend fun createBackup(walletId: String): BackupResult {
        val wallet = getWallet(walletId)
        return if (wallet != null) {
            securityManager.createEncryptedBackup(walletId, wallet)
        } else {
            BackupResult.Error(IllegalArgumentException("Wallet not found"))
        }
    }

    // === HELPER QUERIES ===
    suspend fun hasWallets(): Boolean {
        return localDataSource.hasWallets()
    }

    suspend fun getWalletCount(): Int {
        return localDataSource.getWalletCount()
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

    fun convertToDecimal(balanceStr: String, decimals: Int): String {
        return try {
            val bigInt = BigInteger(balanceStr)
            val divisor = BigDecimal(BigInteger.TEN.pow(decimals))
            BigDecimal(bigInt).divide(divisor).toString()
        } catch (e: Exception) {
            "0"
        }
    }

    // === FLOW MANAGEMENT ===
    private suspend fun updateWalletsFlow(wallet: CryptoWallet) {
        val currentWallets = _walletsFlow.value.toMutableList()
        val existingIndex = currentWallets.indexOfFirst { it.id == wallet.id }

        if (existingIndex >= 0) {
            currentWallets[existingIndex] = wallet
        } else {
            currentWallets.add(wallet)
        }

        _walletsFlow.value = currentWallets
    }

    private suspend fun removeWalletFromFlow(walletId: String) {
        val currentWallets = _walletsFlow.value.toMutableList()
        currentWallets.removeAll { it.id == walletId }
        _walletsFlow.value = currentWallets
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    suspend fun getLiveGasPrice(): GasPrice {
        return blockchainRepository.getCurrentGasPrice()
    }

    fun validateAddress(address: String, walletType: WalletType): Boolean {
        return when (walletType) {
            WalletType.BITCOIN -> blockchainRepository.isValidBitcoinAddress(address)
            WalletType.ETHEREUM -> blockchainRepository.isValidEthereumAddress(address)
            WalletType.SOLANA -> true // For now, accept any string as Solana address
            else -> address.isNotBlank()
        }
    }
}