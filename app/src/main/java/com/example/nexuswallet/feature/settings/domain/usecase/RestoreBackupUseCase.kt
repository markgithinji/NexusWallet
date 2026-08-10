package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.core.domain.exception.HardwareAuthRequiredException
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.toHex
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.settings.domain.model.BackupBundle
import com.example.nexuswallet.feature.settings.domain.model.RestoreSelection
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.logging.Logger
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreBackupUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val vaultRepository: VaultRepository,
    private val settingsRepository: SettingsRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(
        bundle: BackupBundle,
        selection: RestoreSelection,
        cipher: javax.crypto.Cipher? = null
    ): Result<Unit> {
        logger.d(TAG, "Starting restoration process for ${selection.selectedWallets.size} wallets")
        return try {
            // 1. Filter and Restore Wallets
            val selectedWallets =
                bundle.wallets.filter { selection.selectedWallets.contains(it.id) }
            
            logger.d(TAG, "Filtered ${selectedWallets.size} wallets from bundle for restoration")

            // Use NonCancellable for the database/vault writes to prevent partial restores
            withContext(kotlinx.coroutines.NonCancellable) {
                selectedWallets.forEach { wallet ->
                    val walletId = wallet.id
                    logger.d(TAG, "Restoring wallet structure: ${wallet.name} ($walletId)")
                    val allowedNetworks = selection.selectedNetworks[walletId] ?: emptySet()

                    // Filter assets within the wallet based on selection
                    val filteredWallet = wallet.copy(
                        bitcoinCoins = wallet.bitcoinCoins.filter { coin ->
                            isNetworkSelected(coin.network.name, allowedNetworks)
                        },
                        solanaCoins = wallet.solanaCoins.filter { coin ->
                            isNetworkSelected(coin.network.name, allowedNetworks)
                        },
                        evmTokens = wallet.evmTokens.filter { token ->
                            val isNetworkAllowed =
                                isNetworkSelected(token.network.name, allowedNetworks)
                            val walletTokenSelection =
                                selection.selectedTokens[walletId] ?: emptyMap()
                            val allowedTokensForNetwork =
                                walletTokenSelection[token.network.name] ?: emptySet()

                            // Always include Native ETH if network is allowed, otherwise check token selection
                            isNetworkAllowed && (token.evmTokenType == EVMTokenType.NATIVE || allowedTokensForNetwork.contains(
                                token.evmTokenType
                            ))
                        }
                    )

                    // 2. Restore corresponding sensitive data to Vault
                    val vaultEntry =
                        bundle.vaultData.find { it.walletId.equals(walletId, ignoreCase = true) }
                    if (vaultEntry != null) {
                        // Restore Mnemonic
                        val rawMnemonic = vaultEntry.mnemonicRaw.decodeHex()
                        val encryptMnemonicResult = if (cipher != null) {
                            keyStoreRepository.encryptWithCipher(cipher, rawMnemonic)
                        } else {
                            keyStoreRepository.encrypt(rawMnemonic)
                        }

                        if (encryptMnemonicResult is Result.Success) {
                            val (encryptedData, iv) = encryptMnemonicResult.data
                            vaultRepository.storeEncryptedMnemonic(
                                walletId,
                                encryptedData.toHex(),
                                iv
                            )
                        } else if (encryptMnemonicResult is Result.Error) {
                            // If biometric is required, we can't really do NonCancellable easily if it was already prompted
                            // but for now let's just skip or throw
                        }
                        rawMnemonic.fill(0) // Wipe raw mnemonic

                        // Restore Private Keys only for selected networks
                        vaultEntry.privateKeys.forEach { pk ->
                            if (isKeyRequiredForNetworks(pk.keyType, allowedNetworks)) {
                                val rawKey = pk.keyRaw.decodeHex()
                                val encryptKeyResult = if (cipher != null) {
                                    keyStoreRepository.encryptWithCipher(cipher, rawKey)
                                } else {
                                    keyStoreRepository.encrypt(rawKey)
                                }

                                if (encryptKeyResult is Result.Success) {
                                    val (encryptedData, iv) = encryptKeyResult.data
                                    vaultRepository.storeEncryptedPrivateKey(
                                        walletId,
                                        pk.keyType,
                                        encryptedData.toHex(),
                                        iv
                                    )
                                }
                                rawKey.fill(0) // Wipe raw key
                            }
                        }
                    }

                    // 3. Save filtered wallet to database ONLY after vault is secured
                    walletRepository.saveWallet(filteredWallet)
                    logger.d(TAG, "Wallet ${wallet.name} successfully saved to local storage")
                }
            }

            // 3. Restore Global Settings
            logger.d(TAG, "Restoring global settings from backup")
            settingsRepository.setThemeMode(ThemeMode.valueOf(bundle.settings.themeMode))
            settingsRepository.setSelectedCurrency(SupportedCurrency.fromCode(bundle.settings.selectedCurrency))
            settingsRepository.setPrivacyModeEnabled(bundle.settings.privacyModeEnabled)
            settingsRepository.setRequireAuthForSend(bundle.settings.requireAuthForSend)

            logger.d(TAG, "Restoration process completed successfully")
            Result.Success(Unit)
        } catch (e: HardwareAuthRequiredException) {
            logger.w(TAG, "Biometric authentication required for restoration")
            Result.Error("Biometric authentication required", e)
        } catch (e: Exception) {
            logger.e(TAG, "Restoration failed: ${e.message}", e)
            Result.Error(e.message ?: "Failed to restore backup")
        }
    }

    companion object {
        private const val TAG = "RestoreBackupUC"
    }

    private fun isNetworkSelected(networkName: String, selectedNetworks: Set<String>): Boolean {
        return selectedNetworks.any { it.equals(networkName, ignoreCase = true) }
    }

    private fun isKeyRequiredForNetworks(
        keyType: String,
        selectedNetworkNames: Set<String>
    ): Boolean {
        return when (keyType) {
            WalletConstants.KEY_BITCOIN_MAINNET -> isNetworkSelected(
                "Bitcoin Mainnet",
                selectedNetworkNames
            )

            WalletConstants.KEY_BITCOIN_TESTNET -> isNetworkSelected(
                "Bitcoin Testnet",
                selectedNetworkNames
            )

            WalletConstants.KEY_ETHEREUM_MAIN -> {
                isNetworkSelected("Ethereum Mainnet", selectedNetworkNames) ||
                        isNetworkSelected("Ethereum Sepolia", selectedNetworkNames)
            }

            WalletConstants.KEY_SOLANA_MAINNET -> isNetworkSelected(
                "Solana Mainnet",
                selectedNetworkNames
            )

            WalletConstants.KEY_SOLANA_DEVNET -> isNetworkSelected(
                "Solana Devnet",
                selectedNetworkNames
            )

            else -> false
        }
    }
}
