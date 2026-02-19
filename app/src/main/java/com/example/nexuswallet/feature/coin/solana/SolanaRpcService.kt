package com.example.nexuswallet.feature.coin.solana

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface SolanaRpcService {
    @POST("/")
    suspend fun getTransaction(@Body request: RpcRequest): RpcResponse<SolanaTransactionDetailsResponse>
}

@Serializable
sealed class RpcParam {
    @Serializable
    @SerialName("string")
    data class StringParam(val value: String) : RpcParam()

    @Serializable
    @SerialName("int")
    data class IntParam(val value: Int) : RpcParam()

    @Serializable
    @SerialName("long")
    data class LongParam(val value: Long) : RpcParam()

    @Serializable
    @SerialName("boolean")
    data class BooleanParam(val value: Boolean) : RpcParam()

    @Serializable
    @SerialName("object")
    data class ObjectParam(val value: Map<String, RpcParam>) : RpcParam()

    @Serializable
    @SerialName("array")
    data class ArrayParam(val value: List<RpcParam>) : RpcParam()
}

@Serializable
data class RpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int = 1,
    val method: String,
    val params: List<RpcParam>
)

// Helper extension functions for easy creation
fun String.toRpcParam() = RpcParam.StringParam(this)
fun Int.toRpcParam() = RpcParam.IntParam(this)
fun Long.toRpcParam() = RpcParam.LongParam(this)
fun Boolean.toRpcParam() = RpcParam.BooleanParam(this)
fun Map<String, RpcParam>.toRpcParam() = RpcParam.ObjectParam(this)
fun List<RpcParam>.toRpcParam() = RpcParam.ArrayParam(this)

// Example usage
fun createGetTransactionRequest(signature: String): RpcRequest {
    return RpcRequest(
        method = "getTransaction",
        params = listOf(
            signature.toRpcParam(),
            mapOf(
                "commitment" to "confirmed".toRpcParam(),
                "encoding" to "json".toRpcParam(),
                "maxSupportedTransactionVersion" to 0.toRpcParam()
            ).toRpcParam()
        )
    )
}

fun createGetSignaturesRequest(address: String, limit: Int): RpcRequest {
    return RpcRequest(
        method = "getSignaturesForAddress",
        params = listOf(
            address.toRpcParam(),
            mapOf(
                "limit" to limit.toRpcParam(),
                "commitment" to "confirmed".toRpcParam()
            ).toRpcParam()
        )
    )
}

@Serializable
data class RpcResponse<T>(
    val jsonrpc: String,
    val result: T? = null,
    val error: RpcError? = null,
    val id: Int
)

@Serializable
data class RpcError(
    val code: Int,
    val message: String
)