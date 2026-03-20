# Nexus Wallet 🔐 — Cryptocurrency Wallet for Android

**Nexus Wallet** is a modern **cryptocurrency wallet** built with **Kotlin and Jetpack Compose**, designed to showcase advanced Android development skills in security, real-time data handling, and modern architecture. It connects to multiple blockchain APIs (Etherscan, Blockstream, Solana RPC) for secure balance checking and transaction management.

It allows users to **create wallets, manage multiple cryptocurrencies, send and receive transactions**, and monitor **real-time market data**.

> 🧠 **Note:** Nexus Wallet is currently **under active development** and is NOT intended for real cryptocurrency storage. It is a portfolio project demonstrating Android development expertise.

---

## 📸 Screenshots

<div align="center">

![Nexus Wallet App Preview](assets/nexus_screenshot_group.png)
*Wallet dashboard showing portfolio balance, multi-chain assets, and recent transactions*

</div>

---

## 🎯 Project Showcase

### 🔐 **Android Security Expertise**
- **BIP39** mnemonic generation with HD wallet derivation
- **Android KeyStore** encryption for seed phrase protection  
- **Biometric/Facial authentication** with PIN fallback
- **Encrypted local storage** using AndroidX Security Crypto
- **Secure transaction signing** without exposing private keys

### 🌐 **API Integration Mastery**
- **REST API consumption** (CoinGecko for market data)
- **WebSocket implementation** for real-time price updates
- **Public blockchain APIs** (Etherscan, Blockstream) for balance checking
- **Multi-source data aggregation** with offline-first caching

### 🏗️ **Modern Android Architecture**
- **Jetpack Compose** UI with Material Design 3
- **MVVM with Repository pattern** for clean separation
- **StateFlow/SharedFlow** for reactive state management
- **Hilt dependency injection** for testability
- **Navigation Component** with authentication middleware

---

## ✨ Key Features

| Feature | Status | Description |
|---------|--------|-------------|
| 🔐 **Secure Wallet Creation** | ✅ Complete | BIP39 12-word seed phrases with verification |
| 📈 **Real-time Market Data** | ✅ Complete | Live prices with WebSocket updates |
| 👤 **Biometric Authentication** | ✅ Complete | Fingerprint/Face ID + PIN protection |
| 💰 **Multi-Currency Support** | ✅ Complete | Bitcoin, Ethereum, Solana, USDC, USDT |
| 🎨 **Professional UI/UX** | 🔄 In Progress | Jetpack Compose, Material Design 3 |
| 🔄 **Clean Architecture** | ✅ Complete | MVVM, Repository pattern, StateFlow |
| 📊 **Portfolio Tracking** | 🔄 In Progress | Balance aggregation & analytics |
| 🧾 **Transaction History** | 🔄 In Progress | Blockchain API integration |

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **UI** | Jetpack Compose, Material Design 3 | Modern declarative UI |
| **Architecture** | MVVM, Repository Pattern | Clean separation of concerns |
| **DI** | Hilt | Dependency injection |
| **Networking** | Retrofit, OkHttp, WebSocket | API communication |
| **Persistence** | DataStore, Room | Local data storage |
| **Security** | Android KeyStore, Biometric API | Encryption & authentication |
| **Serialization** | Kotlinx Serialization | JSON parsing |
| **Async** | Kotlin Coroutines, Flow | Asynchronous programming |

---

## 📱 Features in Detail

### 🔐 Security First
- **BIP39 Mnemonic Generation**: 12-word seed phrases with verification
- **Android KeyStore Encryption**: Private keys never leave secure hardware
- **Biometric Authentication**: Fingerprint/Face ID with PIN fallback
- **Secure Transaction Signing**: Sign transactions without exposing keys

### 💰 Multi-Chain Support
- **Bitcoin**: Mainnet & Testnet support
- **Ethereum**: Mainnet & Sepolia with ERC-20 tokens (USDC, USDT)
- **Solana**: Mainnet & Devnet support
- **Custom Networks**: Easily extensible for other blockchains

### 📊 Real-time Data
- **Live Price Feeds**: WebSocket connections for market data
- **Balance Updates**: Automatic refresh with pull-to-refresh
- **Transaction Monitoring**: Real-time transaction status

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 11 or higher
- Android SDK API 24+

### Clone & Build
```bash
git clone https://github.com/markgithinji/NexusWallet.git
cd NexusWallet
open in Android Studio
