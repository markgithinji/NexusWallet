package com.example.nexuswallet.feature.authentication.data.repository

import java.io.IOException

// Safe storage call helpers
suspend inline fun <T> safeGet(
    defaultValue: T? = null,
    crossinline block: suspend () -> T?
): T? {
    return try {
        block()
    } catch (e: IOException) {
        defaultValue
    } catch (e: Exception) {
        defaultValue
    }
}

suspend inline fun safeEdit(
    crossinline block: suspend () -> Unit
): Boolean {
    return try {
        block()
        true
    } catch (e: IOException) {
        false
    } catch (e: Exception) {
        false
    }
}