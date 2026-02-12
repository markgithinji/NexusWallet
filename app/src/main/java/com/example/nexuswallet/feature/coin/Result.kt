package com.example.nexuswallet.feature.coin

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    object Loading : Result<Nothing>()
    data class Error(val message: String, val throwable: Throwable? = null) : Result<Nothing>()
}