package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.coin.solana.SolanaNetwork
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
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
    private val secureMnemonicUseCase: SecureMnemonicUseCase,
    private val storePrivateKeyUseCase: StorePrivateKeyUseCase,
    private val createBitcoinCoinUseCase: CreateBitcoinCoinUseCase,
    private val createEthereumCoinUseCase: CreateEthereumCoinUseCase,
    private val createSolanaCoinUseCase: CreateSolanaCoinUseCase,
    private val derivePrivateKeyFromMnemonicUseCase: DerivePrivateKeyFromMnemonicUseCase
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

            // Create coins with validation built into each usecase
            val bitcoinCoin = if (includeBitcoin) {
                when (val result = createBitcoinCoinUseCase(mnemonic, bitcoinNetwork)) {
                    is Result.Success -> result.data
                    is Result.Error -> return Result.Error(result.message, result.throwable)
                    Result.Loading -> return Result.Error("Unexpected loading state while creating Bitcoin coin")
                }
            } else null

            val ethereumCoin = if (includeEthereum) {
                when (val result = createEthereumCoinUseCase(mnemonic, ethereumNetwork)) {
                    is Result.Success -> result.data
                    is Result.Error -> return Result.Error(result.message, result.throwable)
                    Result.Loading -> return Result.Error("Unexpected loading state while creating Ethereum coin")
                }
            } else null

            val solanaCoin = if (includeSolana) {
                when (val result = createSolanaCoinUseCase(mnemonic, solanaNetwork)) {
                    is Result.Success -> result.data
                    is Result.Error -> return Result.Error(result.message, result.throwable)
                    Result.Loading -> return Result.Error("Unexpected loading state while creating Solana coin")
                }
            } else null

            // Create USDC if requested
            val usdcCoin = if (includeUSDC && ethereumCoin != null) {
                USDCCoin(
                    address = ethereumCoin.address,
                    publicKey = ethereumCoin.publicKey,
                    network = ethereumCoin.network,
                    contractAddress = getUSDCContractAddress(ethereumCoin.network)
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
            when (val result = secureMnemonicUseCase(walletId, mnemonic)) {
                is Result.Error -> return Result.Error(result.message, result.throwable)
                Result.Loading -> return Result.Error("Unexpected loading state while securing mnemonic")
                is Result.Success -> { /* Continue */
                }
            }

            // Store Bitcoin private key if included
            if (includeBitcoin) {
                val derivationResult = derivePrivateKeyFromMnemonicUseCase(
                    mnemonic = mnemonic,
                    keyType = "BTC_PRIVATE_KEY",
                    network = bitcoinNetwork
                )

                when (derivationResult) {
                    is Result.Success -> {
                        val storeResult = storePrivateKeyUseCase(
                            walletId,
                            "BTC_PRIVATE_KEY",
                            derivationResult.data
                        )
                        when (storeResult) {
                            is Result.Error -> return Result.Error(
                                storeResult.message,
                                storeResult.throwable
                            )

                            Result.Loading -> return Result.Error("Unexpected loading state while storing Bitcoin key")
                            is Result.Success -> { /* Continue */
                            }
                        }
                    }

                    is Result.Error -> return Result.Error(
                        derivationResult.message,
                        derivationResult.throwable
                    )

                    Result.Loading -> return Result.Error("Unexpected loading state while deriving Bitcoin key")
                }
            }

            // Store Ethereum private key if included
            if (includeEthereum) {
                val derivationResult = derivePrivateKeyFromMnemonicUseCase(
                    mnemonic = mnemonic,
                    keyType = "ETH_PRIVATE_KEY",
                    network = ethereumNetwork
                )

                when (derivationResult) {
                    is Result.Success -> {
                        val storeResult = storePrivateKeyUseCase(
                            walletId,
                            "ETH_PRIVATE_KEY",
                            derivationResult.data
                        )
                        when (storeResult) {
                            is Result.Error -> return Result.Error(
                                storeResult.message,
                                storeResult.throwable
                            )

                            Result.Loading -> return Result.Error("Unexpected loading state while storing Ethereum key")
                            is Result.Success -> { /* Continue */
                            }
                        }
                    }

                    is Result.Error -> return Result.Error(
                        derivationResult.message,
                        derivationResult.throwable
                    )

                    Result.Loading -> return Result.Error("Unexpected loading state while deriving Ethereum key")
                }
            }

            // Store Solana private key if included
            if (includeSolana) {
                val derivationResult = derivePrivateKeyFromMnemonicUseCase(
                    mnemonic = mnemonic,
                    keyType = "SOLANA_PRIVATE_KEY"
                )

                when (derivationResult) {
                    is Result.Success -> {
                        val storeResult = storePrivateKeyUseCase(
                            walletId,
                            "SOLANA_PRIVATE_KEY",
                            derivationResult.data
                        )
                        when (storeResult) {
                            is Result.Error -> return Result.Error(
                                storeResult.message,
                                storeResult.throwable
                            )

                            Result.Loading -> return Result.Error("Unexpected loading state while storing Solana key")
                            is Result.Success -> { /* Continue */
                            }
                        }
                    }

                    is Result.Error -> return Result.Error(
                        derivationResult.message,
                        derivationResult.throwable
                    )

                    Result.Loading -> return Result.Error("Unexpected loading state while deriving Solana key")
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
            return Result.Error("Wallet creation failed: ${e.message}", e)
        }
    }

    private fun getUSDCContractAddress(network: EthereumNetwork): String =
        network.usdcContractAddress
}

@Singleton
class CreateBitcoinCoinUseCase @Inject constructor(
    private val keyValidator: KeyValidator
) {
    operator fun invoke(mnemonic: List<String>, network: BitcoinNetwork): Result<BitcoinCoin> {
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
            if (!keyValidator.isValidBitcoinPrivateKey(privateKeyWIF)) {
                keyValidator.clearKeyFromMemory(privateKeyWIF)
                return Result.Error("Generated invalid Bitcoin private key")
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
            keyValidator.clearKeyFromMemory(privateKeyWIF)

            Result.Success(bitcoinCoin)
        } catch (e: Exception) {
            Result.Error("Failed to create Bitcoin coin: ${e.message}", e)
        }
    }
}

@Singleton
class CreateEthereumCoinUseCase @Inject constructor(
    private val keyValidator: KeyValidator
) {
    companion object {
        private const val HARDENED_BIT = 0x80000000
    }

    operator fun invoke(mnemonic: List<String>, network: EthereumNetwork): Result<EthereumCoin> {
        return try {
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
                    if (isHardened) number or HARDENED_BIT.toInt() else number
                }
                .toIntArray()

            val masterKey = Bip32ECKeyPair.generateKeyPair(seed)
            val derivedKey = Bip32ECKeyPair.deriveKeyPair(masterKey, pathArray)
            val privateKeyHex = "0x${derivedKey.privateKey.toString(16)}"

            // Validate the generated private key
            if (!keyValidator.isValidEthereumPrivateKey(privateKeyHex)) {
                keyValidator.clearKeyFromMemory(privateKeyHex)
                return Result.Error("Generated invalid Ethereum private key")
            }

            val credentials = Credentials.create(derivedKey)
            val ethereumCoin = EthereumCoin(
                address = credentials.address,
                publicKey = derivedKey.publicKeyPoint.getEncoded(false)
                    .joinToString("") { "%02x".format(it) },
                network = network
            )

            // Clear sensitive data
            keyValidator.clearKeyFromMemory(privateKeyHex)

            Result.Success(ethereumCoin)
        } catch (e: Exception) {
            Result.Error("Failed to create Ethereum coin: ${e.message}", e)
        }
    }
}

@Singleton
class CreateSolanaCoinUseCase @Inject constructor(
    private val keyValidator: KeyValidator
) {
    operator fun invoke(mnemonic: List<String>, network: SolanaNetwork): Result<SolanaCoin> {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
            val keypair = deriveSolanaKeypairFromSeed(seed)

            val privateKeyHex = keypair.secret.joinToString("") { "%02x".format(it) }

            // Validate the generated private key
            if (!keyValidator.isValidSolanaPrivateKey(privateKeyHex)) {
                keyValidator.clearKeyFromMemory(privateKeyHex)
                return Result.Error("Generated invalid Solana private key")
            }

            val solanaCoin = SolanaCoin(
                address = keypair.publicKey.toString(),
                publicKey = keypair.publicKey.toString(),
                network = network
            )

            // Clear sensitive data
            keyValidator.clearKeyFromMemory(privateKeyHex)

            Result.Success(solanaCoin)
        } catch (e: Exception) {
            Result.Error("Failed to create Solana coin: ${e.message}", e)
        }
    }

    private fun deriveSolanaKeypairFromSeed(seed: ByteArray): Keypair {
        val hash = MessageDigest.getInstance("SHA-256").digest(seed)

        val expandedSeed = ByteArray(64)
        System.arraycopy(hash, 0, expandedSeed, 0, 32)

        val secondHash = MessageDigest.getInstance("SHA-256").digest(hash)
        System.arraycopy(secondHash, 0, expandedSeed, 32, 32)

        return Keypair.fromSecretKey(expandedSeed)
    }
}

@Singleton
class DerivePrivateKeyFromMnemonicUseCase @Inject constructor(
    private val keyValidator: KeyValidator
) {
    companion object {
        private const val HARDENED_BIT = 0x80000000
    }

    operator fun invoke(
        mnemonic: List<String>,
        keyType: String,
        network: Any? = null
    ): Result<String> {
        return try {
            val privateKey = when (keyType) {
                "BTC_PRIVATE_KEY" -> deriveBitcoinPrivateKey(mnemonic, network)
                "ETH_PRIVATE_KEY" -> deriveEthereumPrivateKey(mnemonic)
                "SOLANA_PRIVATE_KEY" -> deriveSolanaPrivateKey(mnemonic)
                else -> return Result.Error("Unsupported key type: $keyType")
            }

            // Validate the derived private key
            val isValid = when (keyType) {
                "BTC_PRIVATE_KEY" -> keyValidator.isValidBitcoinPrivateKey(privateKey)
                "ETH_PRIVATE_KEY" -> keyValidator.isValidEthereumPrivateKey(privateKey)
                "SOLANA_PRIVATE_KEY" -> keyValidator.isValidSolanaPrivateKey(privateKey)
                else -> false
            }

            if (!isValid) {
                keyValidator.clearKeyFromMemory(privateKey)
                return Result.Error("Derived invalid $keyType private key")
            }

            Result.Success(privateKey)
        } catch (e: Exception) {
            Result.Error("Failed to derive $keyType private key: ${e.message}", e)
        }
    }

    private fun deriveBitcoinPrivateKey(mnemonic: List<String>, network: Any?): String {
        val bitcoinNetwork = network as? BitcoinNetwork ?: BitcoinNetwork.TESTNET
        val params = when (bitcoinNetwork) {
            BitcoinNetwork.MAINNET -> MainNetParams.get()
            BitcoinNetwork.TESTNET -> TestNet3Params.get()
        }
        val seed = DeterministicSeed(mnemonic, null, "", 0L)
        val wallet = BitcoinJWallet.fromSeed(params, seed)
        val key = wallet.currentReceiveKey()
        return key.getPrivateKeyEncoded(params).toString()
    }

    private fun deriveEthereumPrivateKey(mnemonic: List<String>): String {
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
        return "0x${derivedKey.privateKey.toString(16)}"
    }

    private fun deriveSolanaPrivateKey(mnemonic: List<String>): String {
        val seed = MnemonicUtils.generateSeed(mnemonic.joinToString(" "), "")
        val hash = MessageDigest.getInstance("SHA-256").digest(seed)

        val expandedSeed = ByteArray(64)
        System.arraycopy(hash, 0, expandedSeed, 0, 32)

        val secondHash = MessageDigest.getInstance("SHA-256").digest(hash)
        System.arraycopy(secondHash, 0, expandedSeed, 32, 32)

        return expandedSeed.joinToString("") { "%02x".format(it) }
    }
}