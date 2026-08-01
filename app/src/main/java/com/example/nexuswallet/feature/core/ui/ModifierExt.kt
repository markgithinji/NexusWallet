package com.example.nexuswallet.feature.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

/**
 * A clickable modifier that prevents multiple clicks within a short duration.
 */
fun Modifier.clickableSingle(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    debounceTime: Long = 500L,
    onClick: () -> Unit
) = composed {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > debounceTime) {
            lastClickTime = currentTime
            onClick()
        }
    }
}
