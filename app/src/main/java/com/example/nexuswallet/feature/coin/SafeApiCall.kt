package com.example.nexuswallet.feature.coin

// domain/utils/SafeApiCall.kt
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.HttpURLConnection

object SafeApiCall {
    inline fun <T> make(block: () -> T): Result<T> {
        return try {
            Result.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            Result.Error("Connection timeout. Please try again.", e)
        } catch (e: UnknownHostException) {
            Result.Error("No internet connection. Please check your network.", e)
        } catch (e: IOException) {
            Result.Error("Network error. Please check your connection.", e)
        } catch (e: HttpException) {
            val message = when (e.code()) {
                HttpURLConnection.HTTP_BAD_REQUEST -> "Invalid request. Please check your input."
                HttpURLConnection.HTTP_UNAUTHORIZED -> "Authentication failed. Please log in again."
                HttpURLConnection.HTTP_FORBIDDEN -> "You don't have permission to access this resource."
                HttpURLConnection.HTTP_NOT_FOUND -> "Resource not found."
                429 -> "Too many requests. Please try again later."
                in 500..599 -> "Server error. Please try again later."
                else -> "Network error (${e.code()}): ${e.message()}"
            }
            Result.Error(message, e)
        } catch (e: IllegalStateException) {
            Result.Error("Invalid data received from server.", e)
        } catch (e: IllegalArgumentException) {
            Result.Error("Unexpected data format received.", e)
        } catch (e: Exception) {
            Result.Error("Something went wrong. Please try again.", e)
        }
    }
}