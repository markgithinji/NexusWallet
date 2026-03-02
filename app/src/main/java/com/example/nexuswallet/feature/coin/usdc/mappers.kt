package com.example.nexuswallet.feature.coin.usdc

//
//fun TokenTransactionResponse.toDomain(
//    walletId: String,
//    network: EthereumNetwork,
//    walletAddress: String
//): USDCTransaction {
//    // Parse values safely
//    val timestamp = timeStamp.toLongOrNull()?.times(1000) ?: System.currentTimeMillis()
//    val amountValue = value.toBigIntegerOrNull() ?: BigInteger.ZERO
//    val gasPriceValue = gasPrice.toBigIntegerOrNull() ?: BigInteger.ZERO
//    val gasUsedValue = gasUsed.toBigIntegerOrNull() ?: BigInteger.ZERO
//    val gasLimitValue = gas.toLongOrNull() ?: 65000L
//    val nonceValue = nonce.toIntOrNull() ?: 0
//
//    // Calculate fee
//    val feeWeiValue = gasUsedValue.multiply(gasPriceValue)
//    val feeEthValue = BigDecimal(feeWeiValue).divide(
//        BigDecimal("1000000000000000000"),
//        18,
//        RoundingMode.HALF_UP
//    )
//
//    // Convert amount to human readable
//    val decimals = tokenDecimal.toIntOrNull() ?: 6
//    val amountDecimalValue = BigDecimal(amountValue).divide(
//        BigDecimal.TEN.pow(decimals),
//        decimals,
//        RoundingMode.HALF_UP
//    )
//
//    // Determine if incoming
//    val isIncoming = to.equals(walletAddress, ignoreCase = true)
//
//    return USDCTransaction(
//        id = "usdc_${hash}_${System.currentTimeMillis()}",
//        walletId = walletId,
//        fromAddress = from,
//        toAddress = to,
//        status = TransactionStatus.SUCCESS, // Etherscan only returns successful txs
//        timestamp = timestamp,
//        note = null,
//        feeLevel = FeeLevel.NORMAL,
//        amount = amountValue.toString(),
//        amountDecimal = amountDecimalValue.toPlainString(),
//        contractAddress = contractAddress,
//        network = network,
//        gasPriceWei = gasPrice,
//        gasPriceGwei = gasPriceValue.divide(BigInteger("1000000000")).toString(),
//        gasLimit = gasLimitValue,
//        feeWei = feeWeiValue.toString(),
//        feeEth = feeEthValue.toPlainString(),
//        nonce = nonceValue,
//        chainId = network.chainId.toLong(),
//        signedHex = null,
//        txHash = hash,
//        ethereumTransactionId = hash,
//        isIncoming = isIncoming
//    )
//}