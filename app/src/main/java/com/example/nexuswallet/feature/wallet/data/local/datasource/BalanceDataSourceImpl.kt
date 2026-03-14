package com.example.nexuswallet.feature.wallet.data.local.datasource

import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.ethereum.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.solana.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.local.entity.EVMTokenEntity
import com.example.nexuswallet.feature.wallet.data.local.mapper.toDomain
import com.example.nexuswallet.feature.wallet.data.local.mapper.toEntity
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.ethereum.domain.model.TokenType
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceDataSourceImpl @Inject constructor(
    private val bitcoinCoinDao: BitcoinCoinDao,
    private val solanaCoinDao: SolanaCoinDao,
    private val bitcoinBalanceDao: BitcoinBalanceDao,
    private val solanaBalanceDao: SolanaBalanceDao,
    private val evmTokenDao: EVMTokenDao,
    private val evmBalanceDao: EVMBalanceDao
) : BalanceDataSource {

    override suspend fun saveWalletBalance(balance: WalletBalance) {
        // Save Bitcoin balances
        balance.bitcoinBalances.forEach { (network, bitcoinBalance) ->
            val bitcoinCoin = bitcoinCoinDao.getByAddress(bitcoinBalance.address)
            if (bitcoinCoin != null) {
                bitcoinBalanceDao.insert(bitcoinBalance.toEntity(bitcoinCoin.id))
            }
        }

        // Save Solana balances
        balance.solanaBalances.forEach { (network, solanaBalance) ->
            val solanaCoin = solanaCoinDao.getByAddress(solanaBalance.address)
            if (solanaCoin != null) {
                solanaBalanceDao.insert(solanaBalance.toEntity(solanaCoin.id))
            }
        }

        // Save EVM balances
        balance.evmBalances.forEach { evmBalance ->
            val tokenEntity =
                evmTokenDao.getByExternalId(balance.walletId, evmBalance.externalTokenId)
                    ?: createTokenFromBalance(balance.walletId, evmBalance)
            evmBalanceDao.insert(evmBalance.toEntity(balance.walletId, tokenEntity))
        }
    }

    override suspend fun loadWalletBalance(walletId: String): WalletBalance? {
        // Load Bitcoin balances
        val bitcoinBalanceEntities = bitcoinBalanceDao.getByWalletId(walletId)
        val bitcoinBalances = mutableMapOf<BitcoinNetwork, BitcoinBalance>()

        bitcoinBalanceEntities.forEach { balanceEntity ->
            val coin = bitcoinCoinDao.getById(balanceEntity.coinId)
            coin?.network?.let { network ->
                bitcoinBalances[network] = balanceEntity.toDomain()
            }
        }

        // Load Solana balances
        val solanaBalanceEntities = solanaBalanceDao.getByWalletId(walletId)
        val solanaBalances = mutableMapOf<SolanaNetwork, SolanaBalance>()

        solanaBalanceEntities.forEach { balanceEntity ->
            val coin = solanaCoinDao.getById(balanceEntity.coinId)
            coin?.network?.let { network ->
                solanaBalances[network] = balanceEntity.toDomain()
            }
        }

        // Load EVM balances
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

    private suspend fun createTokenFromBalance(
        walletId: String,
        evmBalance: EVMBalance
    ): EVMTokenEntity {
        val parts = evmBalance.externalTokenId.split("_", limit = 2)
        val chainId = parts.getOrNull(0) ?: "1"
        val tokenIdentifier = parts.getOrNull(1) ?: "unknown"

        val network = when (chainId) {
            "1" -> EthereumNetwork.Mainnet
            "11155111" -> EthereumNetwork.Sepolia
            else -> EthereumNetwork.Mainnet
        }

        val tokenType = when (tokenIdentifier) {
            "eth" -> TokenType.NATIVE
            "usdc" -> TokenType.USDC
            "usdt" -> TokenType.USDT
            else -> TokenType.ERC20
        }

        val tokenEntity = EVMTokenEntity(
            id = UUID.randomUUID().toString(),
            walletId = walletId,
            address = evmBalance.address,
            publicKey = "",
            derivationPath = "m/44'/60'/0'/0/0",
            network = network,
            contractAddress = when (tokenType) {
                TokenType.NATIVE -> "0x0000000000000000000000000000000000000000"
                TokenType.USDC -> network.usdcContractAddress
                TokenType.USDT -> network.usdtContractAddress
                TokenType.ERC20 -> tokenIdentifier  // Fallback for unknown tokens
            },
            symbol = when (tokenType) {
                TokenType.NATIVE -> "ETH"
                TokenType.USDC -> "USDC"
                TokenType.USDT -> "USDT"
                TokenType.ERC20 -> "UNKNOWN"
            },
            name = when (tokenType) {
                TokenType.NATIVE -> "Ethereum"
                TokenType.USDC -> "USD Coin"
                TokenType.USDT -> "Tether USD"
                TokenType.ERC20 -> "Unknown Token"
            },
            decimals = when (tokenType) {
                TokenType.NATIVE -> 18
                TokenType.USDC, TokenType.USDT -> 6
                TokenType.ERC20 -> 18
            },
            tokenType = tokenType,
            externalId = evmBalance.externalTokenId,
            updatedAt = System.currentTimeMillis()
        )

        evmTokenDao.insert(tokenEntity)
        return tokenEntity
    }
}