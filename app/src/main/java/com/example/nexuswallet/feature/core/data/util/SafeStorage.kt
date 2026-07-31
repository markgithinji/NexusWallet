package com.example.nexuswallet.feature.core.data.util

import java.io.IOException

/**
 * Safely executes a DataStore read operation.
 * Catches [IOException] which is expected when reading from disk,
 * but allows other [RuntimeException]s to bubble up to avoid hiding logic bugs.
 */
suspend inline fun <T> safeGet(
    defaultValue: T? = null,
    crossinline block: suspend () -> T?
): T? {
    return try {
        block()
    } catch (e: IOException) {
        defaultValue
    }
}

/**
 * Safely executes a DataStore edit operation.
 * Returns true if successful, false if an [IOException] occurred.
 * Other exceptions are rethrown to avoid masking development errors.
 */
suspend inline fun safeEdit(
    crossinline block: suspend () -> Unit
): Boolean {
    return try {
        block()
        true
    } catch (e: IOException) {
        false
    }
}
