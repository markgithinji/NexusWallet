package com.example.nexuswallet.feature.coin.usdc.domain

//
//@Module
//@InstallIn(SingletonComponent::class)
//object UseCaseModule {
//
//    @Provides
//    @Singleton
//    fun provideGetTransactionUseCase(
//        ethereumTransactionRepository: EthereumTransactionRepository,
//        logger: Logger
//    ): GetTransactionUseCase {
//        return GetTransactionUseCaseImpl(
//            ethereumTransactionRepository,
//            logger
//        )
//    }
//
//    @Provides
//    @Singleton
//    fun provideGetWalletTransactionsUseCase(
//        ethereumTransactionRepository: EthereumTransactionRepository,
//        logger: Logger
//    ): GetWalletTransactionsUseCase {
//        return GetWalletTransactionsUseCaseImpl(
//            ethereumTransactionRepository,
//            logger
//        )
//    }
//
//    @Provides
//    @Singleton
//    fun provideGetPendingTransactionsUseCase(
//        ethereumTransactionRepository: EthereumTransactionRepository,
//        logger: Logger
//    ): GetPendingTransactionsUseCase {
//        return GetPendingTransactionsUseCaseImpl(
//            ethereumTransactionRepository,
//            logger
//        )
//    }
//}