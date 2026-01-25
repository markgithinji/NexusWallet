package com.example.nexuswallet

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity

@Composable
fun getFragmentActivity(): FragmentActivity? {
    val context = LocalContext.current
    return if (context is FragmentActivity) {
        context
    } else {
        null
    }
}