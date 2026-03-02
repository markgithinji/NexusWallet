package com.example.nexuswallet.feature.wallet.data.local

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMTokenDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLTokenDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toBitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toDomain
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toEntity
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toSolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toStorageString
import com.example.nexuswallet.feature.wallet.domain.WalletLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletLocalDataSourceImpl @Inject constructor(
    private val walletDao: WalletDao,
    private val bitcoinCoinDao: BitcoinCoinDao,
    private val solanaCoinDao: SolanaCoinDao,
    private val bitcoinBalanceDao: BitcoinBalanceDao,
    private val solanaBalanceDao: SolanaBalanceDao,
    private val evmTokenDao: EVMTokenDao,
    private val evmBalanceDao: EVMBalanceDao,
    private val splTokenDao: SPLTokenDao
) : WalletLocalDataSource {

    // === Wallet Operations ===
    override suspend fun saveWallet(wallet: Wallet) {
        // Save main wallet entity
        walletDao.insert(wallet.toEntity())

        // Save Bitcoin coins
        wallet.bitcoinCoins.forEach { coin ->
            bitcoinCoinDao.insert(coin.toEntity(wallet.id))
        }

        // Save Solana coins
        wallet.solanaCoins.forEach { coin ->
            // Create the entity first so we can get its ID
            val solanaCoinEntity = coin.toEntity(wallet.id)

            // Insert the Solana coin and capture its ID
            solanaCoinDao.insert(solanaCoinEntity)

            // Now use the entity's ID (which was generated in toEntity) to save SPL tokens
            coin.splTokens.forEach { splToken ->
                splTokenDao.insert(splToken.toEntity(solanaCoinEntity.id))
            }
        }

        // Save EVM tokens
        wallet.evmTokens.forEach { token ->
            evmTokenDao.insert(token.toEntity(wallet.id))
        }
    }

    override suspend fun loadWallet(walletId: String): Wallet? {
        val walletEntity = walletDao.get(walletId) ?: return null

        // Load Bitcoin coins
        val bitcoinCoins = bitcoinCoinDao.getByWalletId(walletId)
            .map { it.toDomain() }

        // Load Solana coins with their SPL tokens
        val solanaCoins = solanaCoinDao.getByWalletId(walletId)
            .map { solanaEntity ->
                solanaEntity.toDomain(
                    splTokens = splTokenDao.getBySolanaCoinId(solanaEntity.id).map { splEntity ->
                        splEntity.toDomain()
                    }
                )
            }

        // Load EVM tokens
        val evmTokens = evmTokenDao.getByWalletId(walletId).map { entity ->
            entity.toDomain()
        }

        return walletEntity.toDomain(
            bitcoinCoins = bitcoinCoins,
            solanaCoins = solanaCoins,
            evmTokens = evmTokens
        )
    }

    override fun loadAllWallets(): Flow<List<Wallet>> {
        return walletDao.getAll().map { entities ->
            entities.map { entity ->
                // Load Bitcoin coins
                val bitcoinCoins = bitcoinCoinDao.getByWalletId(entity.id)
                    .map { it.toDomain() }

                // Load Solana coins with their SPL tokens
                val solanaCoins = solanaCoinDao.getByWalletId(entity.id)
                    .map { solanaEntity ->
                        solanaEntity.toDomain(
                            splTokens = splTokenDao.getBySolanaCoinId(solanaEntity.id).map { splEntity ->
                                splEntity.toDomain()
                            }
                        )
                    }

                // Load EVM tokens
                val evmTokens = evmTokenDao.getByWalletId(entity.id).map { tokenEntity ->
                    tokenEntity.toDomain()
                }

                entity.toDomain(
                    bitcoinCoins = bitcoinCoins,
                    solanaCoins = solanaCoins,
                    evmTokens = evmTokens
                )
            }
        }
    }

    override suspend fun deleteWallet(walletId: String) {
        walletDao.delete(walletId)
        // Coins, tokens, and balances are deleted automatically via CASCADE foreign keys
        // No need to manually delete from tables
    }

    // === Balance Operations ===
    override suspend fun saveWalletBalance(balance: WalletBalance) {
        // Save Bitcoin balances
        balance.bitcoinBalances.forEach { (network, bitcoinBalance) ->
            val bitcoinCoin = bitcoinCoinDao.getByAddress(bitcoinBalance.address)
            if (bitcoinCoin != null) {
                bitcoinBalanceDao.insert(bitcoinBalance.toEntity(bitcoinCoin.id))
            } else {
                Log.e("WalletLocalDS", "No Bitcoin coin found for address: ${bitcoinBalance.address}")
            }
        }

        // Save Solana balances
        balance.solanaBalances.forEach { (network, solanaBalance) ->
            val solanaCoin = solanaCoinDao.getByAddress(solanaBalance.address)
            if (solanaCoin != null) {
                solanaBalanceDao.insert(solanaBalance.toEntity(solanaCoin.id))
            } else {
                Log.e("WalletLocalDS", "No Solana coin found for address: ${solanaBalance.address}")
            }
        }

        // Save EVM balances
        balance.evmBalances.forEach { evmBalance ->
            val tokenEntity = evmTokenDao.getByExternalId(balance.walletId, evmBalance.externalTokenId)
                ?: run {
                    val newTokenEntity = createTokenFromBalance(balance.walletId, evmBalance)
                    evmTokenDao.insert(newTokenEntity)
                    newTokenEntity
                }
            evmBalanceDao.insert(evmBalance.toEntity(balance.walletId, tokenEntity))
        }
    }

    // Helper method to create token from balance
    private fun createTokenFromBalance(walletId: String, evmBalance: EVMBalance): EVMTokenEntity {
        val parts = evmBalance.externalTokenId.split("_", limit = 2)
        val chainId = parts.getOrNull(0) ?: "1"
        val tokenIdentifier = parts.getOrNull(1) ?: "unknown"

        val network = when (chainId) {
            "1" -> EthereumNetwork.Mainnet
            "11155111" -> EthereumNetwork.Sepolia
            else -> EthereumNetwork.Mainnet
        }

        return EVMTokenEntity(
            id = UUID.randomUUID().toString(),
            walletId = walletId,
            address = evmBalance.address,
            publicKey = "",
            derivationPath = "m/44'/60'/0'/0/0",
            network = network.toStorageString(),
            contractAddress = when (tokenIdentifier) {
                "eth" -> "0x0000000000000000000000000000000000000000"
                "usdc" -> network.usdcContractAddress
                "usdt" -> when (network) {
                    EthereumNetwork.Mainnet -> "0xdAC17F958D2ee523a2206206994597C13D831ec7"
                    EthereumNetwork.Sepolia -> "0x7169D38820dfd117C3FA1f22a697dBA58d90BA06"
                }
                else -> tokenIdentifier
            },
            symbol = when (tokenIdentifier) {
                "eth" -> "ETH"
                "usdc" -> "USDC"
                "usdt" -> "USDT"
                else -> "UNKNOWN"
            },
            name = when (tokenIdentifier) {
                "eth" -> "Ethereum"
                "usdc" -> "USD Coin"
                "usdt" -> "Tether USD"
                else -> "Unknown Token"
            },
            decimals = when (tokenIdentifier) {
                "eth" -> 18
                "usdc", "usdt" -> 6
                else -> 18
            },
            tokenType = when (tokenIdentifier) {
                "eth" -> "NATIVE"
                "usdc" -> "USDC"
                "usdt" -> "USDT"
                else -> "ERC20"
            },
            externalId = evmBalance.externalTokenId,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun loadWalletBalance(walletId: String): WalletBalance? {
        // Load Bitcoin balances
        val bitcoinBalanceEntities = bitcoinBalanceDao.getByWalletId(walletId)
        val bitcoinBalances = mutableMapOf<String, BitcoinBalance>()

        bitcoinBalanceEntities.forEach { balanceEntity ->
            val coin = bitcoinCoinDao.getById(balanceEntity.coinId)
            val networkKey = when (coin?.network?.toBitcoinNetwork()) {
                BitcoinNetwork.Mainnet -> "mainnet"
                BitcoinNetwork.Testnet -> "testnet"
                else -> null
            }
            if (networkKey != null) {
                bitcoinBalances[networkKey] = balanceEntity.toDomain()
                Log.d("WalletLocalDS", "Loaded Bitcoin $networkKey: ${balanceEntity.btc} (coin network: ${coin?.network})")
            }
        }

        // Load Solana balances
        val solanaBalanceEntities = solanaBalanceDao.getByWalletId(walletId)
        val solanaBalances = mutableMapOf<String, SolanaBalance>()

        solanaBalanceEntities.forEach { balanceEntity ->
            val coin = solanaCoinDao.getById(balanceEntity.coinId)
            Log.d("WalletLocalDS", "Processing Solana balance for coinId: ${balanceEntity.coinId}, coin: $coin")

            val networkKey = when (coin?.network?.toSolanaNetwork()) {
                SolanaNetwork.Mainnet -> "mainnet"
                SolanaNetwork.Devnet -> "devnet"
                else -> null
            }

            Log.d("WalletLocalDS", "Coin network string: ${coin?.network}, parsed: ${coin?.network?.toSolanaNetwork()}, key: $networkKey")

            if (networkKey != null) {
                solanaBalances[networkKey] = balanceEntity.toDomain()
                Log.d("WalletLocalDS", " Loaded Solana $networkKey: ${balanceEntity.sol} SOL")
            } else {
                Log.e("WalletLocalDS", " Could not determine network for coin ${coin?.id} with network string: ${coin?.network}")
            }
        }

        val evmBalances = evmBalanceDao.getByWalletId(walletId).map { entity ->
            entity.toDomain()
        }

        return if (bitcoinBalances.isEmpty() && solanaBalances.isEmpty() && evmBalances.isEmpty()) {
            null
        } else {
            WalletBalance(
                walletId = walletId,
                lastUpdated = System.currentTimeMillis(),
                bitcoinBalances = bitcoinBalances,
                solanaBalances = solanaBalances,
                evmBalances = evmBalances
            )
        }
    }
}