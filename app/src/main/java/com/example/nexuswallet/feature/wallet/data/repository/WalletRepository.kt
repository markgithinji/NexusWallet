package com.example.nexuswallet.feature.wallet.data.repository


import android.util.Log
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
import java.math.BigDecimal
import java.math.BigInteger
import java.security.SecureRandom
import javax.inject.Inject
import org.bitcoinj.wallet.Wallet as BitcoinJWallet

class WalletRepository @Inject constructor(
    private val localDataSource: WalletLocalDataSource,
    private val securityManager: SecurityManager,
    private val blockchainRepository: BlockchainRepository
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

    fun createEthereumWallet(
        mnemonic: List<String>,
        name: String,
        network: EthereumNetwork = EthereumNetwork.MAINNET
    ): EthereumWallet {
        val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")

        // Derive based on network
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
        val credentials = Credentials.create(derivedKey)

        return EthereumWallet(
            id = "eth_${System.currentTimeMillis()}",
            name = name,
            address = credentials.address,
            publicKey = derivedKey.publicKeyPoint.getEncoded(false).toHex(),
            privateKeyEncrypted = "",
            network = network,
            derivationPath = derivationPath,
            isSmartContractWallet = false,
            walletFile = null,
            mnemonicHash = mnemonic.hashCode().toString(),
            createdAt = System.currentTimeMillis(),
            isBackedUp = false,
            walletType = WalletType.ETHEREUM
        )
    }

    fun createMultiChainWallet(
        mnemonic: List<String>,
        name: String
    ): MultiChainWallet {
        val bitcoinWallet = createBitcoinWallet(mnemonic, "$name (Bitcoin)")
        val ethereumWallet = createEthereumWallet(mnemonic, "$name (Ethereum)")
        val polygonWallet = createEthereumWallet(mnemonic, "$name (Polygon)").copy(
            network = EthereumNetwork.POLYGON
        )
        val bscWallet = createEthereumWallet(mnemonic, "$name (BSC)").copy(
            network = EthereumNetwork.BSC
        )

        // Use Ethereum address as the primary address, or Bitcoin if Ethereum is null
        val primaryAddress = ethereumWallet.address ?: bitcoinWallet.address

        return MultiChainWallet(
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
            // Get real balance from blockchain
            val ethBalance = blockchainRepository.getEthereumBalance(wallet.address)
            Log.d("WalletRepo", "ETH Balance for ${wallet.address}: $ethBalance")

            // Get token balances
            val tokens = blockchainRepository.getTokenBalances(
                wallet.address,
                ChainId.ETHEREUM_MAINNET
            )

            // Get transactions
            val transactions = blockchainRepository.getEthereumTransactions(wallet.address)

            // Create updated balance
            val balance = WalletBalance(
                walletId = wallet.id,
                address = wallet.address,
                nativeBalance = (ethBalance * BigDecimal("1000000000000000000")).toPlainString(),
                nativeBalanceDecimal = ethBalance.toPlainString(),
                usdValue = calculateUsdValue(ethBalance, "ETH"),
                tokens = tokens
            )

            // Save balance
            saveWalletBalance(balance)

            // Save transactions
            saveTransactions(wallet.id, transactions)

            Log.d("WalletRepo", "Ethereum wallet synced successfully")

        } catch (e: Exception) {
            Log.e("WalletRepo", "Error syncing Ethereum wallet: ${e.message}")
            // Fallback to sample data
            val fallbackBalance = createSampleBalance(wallet.id, wallet.address)
            saveWalletBalance(fallbackBalance)
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
                val ethBalance = blockchainRepository.getEthereumBalance(ethWallet.address)
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

    // Add Solana wallet sync method
    private suspend fun syncSolanaWallet(wallet: SolanaWallet) {
        try {
            Log.d("WalletRepo", "Solana wallet sync not implemented yet, using demo data")

            val solBalance = BigDecimal("5.75") // Demo SOL balance
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

    // Private helper
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // === NEW HELPER METHODS ===
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