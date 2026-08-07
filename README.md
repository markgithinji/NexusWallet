# Nexus Wallet , Cryptocurrency Wallet for Android

**Nexus Wallet** is a self-hosted **offline-first** cryptocurrency wallet built with **Kotlin and Jetpack Compose**, showcasing advanced Android development skills in security, real-time data handling, and modern architecture. It connects to multiple blockchain APIs (Etherscan, Blockstream, Solana RPC) for secure balance checking and transaction management.

It allows users to **create wallets, manage multiple cryptocurrencies, send and receive transactions**, and monitor **real-time market data**.

> 🧠 **Note:** Nexus Wallet is a portfolio project demonstrating Android development expertise. Since it is under active development, **avoid using it for real cryptocurrency storage**.

---

## 📸 Screenshots

<div align="center">

<img src="assets/nexus_screenshot_group.png" width="800" alt="Nexus Wallet App Preview">

*Wallet dashboard showing portfolio balance, multi-chain assets, and recent transactions*

</div>

---

## 🎯 Project Showcase

### 🔐 **Android Security Expertise**
- **BIP39 mnemonic generation** with 12-word seed phrases and verification step
- **Secure Wallet Import** - Industry-standard BIP39 restoration with real-time word validation
- **Hierarchical Deterministic (HD) wallet derivation** for multiple cryptocurrencies
- **Android KeyStore encryption** - Hardware-backed security using **TEE (Trusted Execution Environment)** and **StrongBox** (where available) to ensure keys are isolated from the main OS.
- **Biometric-Locked Keys** - Uses `setUserAuthenticationRequired(true)` so that sensitive keys can only be decrypted immediately following a successful biometric event, providing military-grade protection even on compromised devices.
- **Biometric authentication** (fingerprint/face ID) with PIN fallback
- **Encrypted local storage** using AndroidX Security Crypto for sensitive data
- **Offline-first architecture** with caching for balances, transactions, and wallet data; works without internet connection
- **Self-hosted architecture** with local-only key storage, no backend server
- **Secure transaction signing** without exposing private keys to the UI layer
- **Mnemonic Memory Hardening** - Uses **CharArray** throughout the derivation chain to ensure seed phrases never linger in the JVM String Pool, providing defense-in-depth against memory dump attacks.
- **Authentication middleware** for protected routes requiring biometric verification

### 🌐 **Multi-Blockchain Integration & Real-time Data**
- **Bitcoin** - Blockstream API integration for mainnet and testnet with RPC fallback
- **Ethereum** - Etherscan API + Web3j JSON-RPC with Sepolia testnet support and optimized gas handling for USDT.
- **Solana** - Helius RPC API + Sol4k with localized priority fee estimation and robust transaction confirmation polling.
- **ERC-20 Token Support** - USDC and USDT with proper decimal handling (6 decimals for USDC/USDT)
- **SPL Token Support** - Solana token program integration for SPL tokens
- **Real-time price updates** - WebSocket connection to Binance WebSocket API for live cryptocurrency prices
- **Market data aggregation** - CoinGecko REST API for historical data and market trends
- **Crypto news aggregation** - CoinStats API for latest cryptocurrency news
- **JSON-RPC client** - Direct blockchain node communication via Web3j for Ethereum and custom RPC for Bitcoin/Solana
- **Multi-Chain Live Subscriptions** - Real-time balance updates using WebSockets for Bitcoin (Mempool.space), Ethereum (Alchemy), and Solana (Helius); app reacts instantly to on-chain activity without manual polling.

### 🔌 **Real-time Data & WebSocket Integration**
- **Address Tracking** - Instant detection of transactions via `accountSubscribe` (Solana), `eth_subscribe` (Ethereum), and `track-address` (Bitcoin).
- **Unified Messaging** - Centralized management of persistent connections for both market data and blockchain events using services and repositories.
- **Automatic Reconnection** - Robust handling of network switches and drops using exponential backoff.
- **Reactive UI** - Balance "ticking" and portfolio animations that update without manual refresh.
- **Background Data Sync** - Efficient event-driven updates using Coroutine Flows to minimize API usage.
  
### 🏗️ **Modern Android Architecture**
- **Jetpack Compose UI** with Material Design 3
- **MVVM with Repository pattern** for clean separation of concerns
- **StateFlow/SharedFlow** for reactive state management
- **Hilt dependency injection** for testable and modular code
- **Fee UI Mapping** - Centralized mapping layer to decouple network-specific fee estimates from the presentation layer, ensuring UI consistency across BTC, ETH, and SOL.
- **Navigation Component** with typed navigation and authentication middleware
- **Room Database** for offline-first caching of balances, transactions, and wallets
- **Coroutine Flows** for reactive data streams from database and network
- **Unified Transaction Flow** - Generic state management for multi-chain transaction lifecycles using a single source of truth for loading, success, and error states.
- **Type-safe serialization** with Kotlinx Serialization

---

## 💎 **Advanced Features**

### Wallet Management
- **Multi-wallet support** - Create and manage multiple wallets
- **Network selection** - Choose specific mainnets and testnets to "bring back" during the import process
- **Token selection** - Choose which tokens to enable per wallet
- **Wallet backup** - Seed phrase display with security checklist
- **Local Encrypted Backup** - Export all wallets and settings into a secure, portable file encrypted with your PIN (AES-GCM)
- **Wallet restoration** - Seamlessly import existing wallets from any BIP39-compliant app (MetaMask, Trust Wallet, etc.) or restore from a Nexus backup file
- **Wallet deletion** - Secure wallet removal
- **Address Book** - Save frequently used addresses with aliases (e.g., "Exchange", "Cold Wallet") to reduce copy-paste errors across all supported chains

### Transaction Capabilities
- **Send transactions** - Native ETH, BTC, SOL, and ERC-20/SPL tokens
- **QR Code Scanning** - Modern, portrait-mode camera integration for instant recipient address entry with real-time multi-chain validation.
- **Real-time Fiat Conversion** - Bidirectional input support (Crypto ⇄ Fiat) with instant calculation and a discoverable UI toggle for seamless switching.
- **Receive transactions** - Generate QR codes for receiving addresses
- **Transaction review screen** - Review transaction details before sending
- **Fee level selection** - Slow, Normal, Fast priority fees
- **Max amount support** - Send entire balance minus network fees
- **Transaction history** - View recent and all transactions
- **Transaction details** - View transaction hash, fees, and explorer links
- **Address Book Integration** - Quick access to saved contacts during the send flow to prevent errors
- **Transaction Monitoring** - Real-time background monitoring for outgoing transactions with local notifications upon confirmation

### Security Features
- **Biometric authentication** - Fingerprint or Face ID for app access and sensitive operations
- **PIN Protection** - Secure 6-digit PIN with haptic feedback and encrypted storage
- **Privacy Mode** - Toggle to hide sensitive balances on the main dashboard
- **Transaction Security** - Optional setting to require re-authentication for every outgoing transaction
- **Portable Backups** - Industry-standard AES-GCM encryption for wallet exports, allowing secure migration between devices using a PIN-derived key
- **Secure Key Storage** - Keys are secured in the hardware-backed **TEE or StrongBox** and are biometric-locked; they are never stored in plain text and never touch non-secure RAM.
- **Transaction Validation** - Real-time address validation (including Solana PDA/Program detection), balance checks, and self-send protection.
- **Secure Data Management** - Option to wipe all sensitive data and keys from the device

### Portfolio & Settings
- **Real-time portfolio tracking** - Total value across all wallets with animated updates
- **Price change indicators** - Visual 24h price trends for all assets
- **Multi-currency support** - Display balances in USD, EUR, GBP, JPY, AUD, CAD, or KES
- **Asset breakdown** - Detailed view per cryptocurrency including transaction history and tokens
- **Market data** - Live crypto prices, rank, market cap, and historical charts
- **Crypto News** - Latest blockchain news aggregated from multiple sources

### User Experience
- **Seed phrase security checklist** - Guided backup process with verification steps
- **Transaction status tracking** - Real-time status updates with explorer links after sending
- **Pre-transaction confirmation** - Dedicated review screen showing amount, recipient, and fees before signing
- **Destructive action confirmation** - Confirmation dialogs for dangerous actions
- **Pull-to-refresh** - Manual refresh of balances and transactions
- **Skeleton loading states** - Smooth loading experience
- **QR Code Integration** - Quick-scan for sending funds and high-quality generation for receiving addresses.
- **Error handling** - User-friendly error messages with retry options
- **Copy to clipboard** - Easy copying of addresses and transaction hashes
- **Share transactions** - Share transaction details with others
- **Toast notifications** - Immediate feedback for user actions

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **UI** | Jetpack Compose, Material Design 3 | Modern declarative UI with animations |
| **Architecture** | MVI, MVVM, Repository Pattern | Clean separation of concerns |
| **DI** | Hilt | Dependency injection |
| **Networking** | Retrofit, OkHttp, WebSockets (Binance, Mempool, Alchemy, Helius), JSON-RPC | API communication, real-time streaming, and blockchain RPC calls |
| **Persistence** | Room Database, DataStore | Local data caching |
| **Security** | Android KeyStore, Biometric API, Security Crypto | Encryption & authentication |
| **Serialization** | Kotlinx Serialization | Type-safe JSON parsing |
| **Async** | Kotlin Coroutines, StateFlow, SharedFlow | Asynchronous programming |
| **Navigation** | Compose Navigation | Type-safe routing with authentication |
| **Blockchain** | BitcoinJ, Web3j, Sol4k | Blockchain interaction libraries |
| **QR Code** | ZXing Android Embedded | QR code generation and scanning |

---

## 📱 Feature Roadmap

| Feature | Status | Description |
|---------|--------|-------------|
| ✅ Wallet Creation | Complete | BIP39 seed phrase with verification |
| ✅ Biometric Authentication | Complete | Fingerprint/Face ID + PIN protection |
| ✅ Multi-Wallet Support | Complete | Create and manage multiple wallets |
| ✅ Bitcoin Support | Complete | Mainnet & testnet transactions |
| ✅ Ethereum Support | Complete | Mainnet & Sepolia with ERC-20 tokens |
| ✅ Solana Support | Complete | Mainnet & devnet with SPL tokens |
| ✅ Token Support | Complete | USDC and USDT on EVM chains |
| ✅ Send/Receive | Complete | Full transaction flow |
| ✅ Transaction History | Complete | Recent and all transactions |
| ✅ Market Data | Complete | Live prices from CoinGecko |
| ✅ Portfolio Tracking | Complete | Total value across all assets |
| ✅ Live Balance Updates | Complete | Real-time WebSocket subscriptions for all chains |
| ✅ Crypto News | Complete | Aggregated news from CoinStats |
| ✅ Transaction Review | Complete | Confirm details before sending |
| ✅ Wallet Import | Complete | Restore any BIP39-compliant wallet with full asset selection |
| ✅ Price Charts | Complete | Historical price graphs |
| ✅ Privacy Mode | Complete | Hide balances from the main screen |
| ✅ Multi-Currency | Complete | Support for USD, EUR, GBP, KES, etc. |
| ✅ Local Backup | Complete | AES-GCM encrypted export/import via user PIN |
| ✅ Address Book | Complete | Manage saved addresses with aliases for easy sending |
| ✅ Transaction Notifications | Complete | Background monitoring and local notifications for confirmed transactions |
| 🔄 Cloud Backup | Planned | Encrypted backup to Google Drive |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or higher
- Android SDK API 24+ (Android 7.0)
- Device or emulator with biometric hardware (for fingerprint/face ID)

### API Keys Required

To run the app, you'll need to obtain API keys from the following services and add them to `local.properties`:

```properties
# Ethereum
ETHERSCAN_API_KEY=your_etherscan_api_key
ALCHEMY_API_KEY=your_alchemy_api_key

# Market Data
COINGECKO_API_KEY=your_coingecko_api_key
COINSTATS_API_KEY=your_coinstats_api_key

# Solana
HELIUS_API_KEY=your_helius_api_key
```

### Free API Sign-up Links:
- [Etherscan API](https://etherscan.io/apis) - Free tier: 5 requests/second
- [Alchemy](https://www.alchemy.com/) - Free tier: 300M compute units/month
- [CoinGecko API](https://www.coingecko.com/en/api) - Free tier: 50 calls/minute
- [CoinStats API](https://coinstats.app/developers/) - News & Market Data
- [Helius](https://helius.xyz/) - Free tier: 50 requests/second

## 🚀 Getting Started

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/markgithinji/NexusWallet.git
   cd NexusWallet
   ```
2. **Create `local.properties` file**
   ```bash
   echo 'sdk.dir=/path/to/android/sdk' > local.properties
   ```
3. **Add API keys to `local.properties`**
   ```properties
   ETHERSCAN_API_KEY=your_key_here
   ALCHEMY_API_KEY=your_key_here
   COINGECKO_API_KEY=your_key_here
   COINSTATS_API_KEY=your_key_here
   HELIUS_API_KEY=your_key_here
   ```
4. **Open in Android Studio**
   - File → Open → Select the project folder
   - Wait for Gradle sync to complete

5. **Run the app**
   - Select a device/emulator
   - Click Run (▶️) button

---

## 🧪 Testing

### Manual Testing
- Create wallet with different network selections
- Test authentication with fingerprint/face ID
- Send and receive transactions (use testnet funds)
- Refresh data with pull-to-refresh
- View transaction details and explorer links
- Delete wallets and verify data removal

### 🚰 Testnet Faucets
For testing transactions without real value, use these official faucets:

| Asset | Network | Faucet Link |
|-------|---------|-------------|
| **BTC** | Bitcoin Testnet | [Bitcoin Coinfaucet](https://coinfaucet.eu/en/btc-testnet/) |
| **ETH** | Ethereum Sepolia | [Sepolia Faucet](https://sepoliafaucet.com/) or [Google Cloud](https://cloud.google.com/application/web3/faucet/ethereum/sepolia) |
| **SOL** | Solana Devnet | [Solana Faucet](https://faucet.solana.com/) |
| **USDC** | Sepolia / Devnet | [Circle Faucet](https://faucet.circle.com/) |


---

## 🤝 Contributing

This is a portfolio project, but contributions and feedback are welcome! Feel free to:
- Open issues for bugs or feature requests
- Submit pull requests for improvements
- Share suggestions for architecture or UI improvements


---

## 🙏 Acknowledgments

- **BitcoinJ** - Bitcoin protocol implementation
- **Web3j** - Ethereum integration library
- **Sol4k** - Solana blockchain library
- **CoinGecko** - Cryptocurrency price data
- **Etherscan** - Ethereum blockchain explorer API
- **Blockstream** - Bitcoin blockchain explorer API
- **Helius** - Solana RPC infrastructure
- **Material Design 3** - Design system and components

---

**Built with ❤️ using Kotlin and Jetpack Compose**
   
