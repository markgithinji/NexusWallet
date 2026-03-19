package com.example.nexuswallet.feature.bitcoin.data.remote.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * A custom Retrofit [Converter.Factory] that handles plain text (String) requests and responses.
 *
 * This factory is used when the API communicates using raw strings instead of structured
 * data formats like JSON. It converts [ResponseBody] directly to a [String] and
 * wraps [String] values into a "text/plain" [RequestBody].
 */
class PlainTextConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        return if (type == String::class.java) {
            Converter { it.string() }
        } else {
            null
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        return if (type == String::class.java) {
            Converter<String, RequestBody> { value ->
                value.toRequestBody("text/plain".toMediaType())
            }
        } else {
            null
        }
    }
}