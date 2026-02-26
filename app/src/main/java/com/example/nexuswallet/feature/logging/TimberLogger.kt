package com.example.nexuswallet.feature.logging

import android.util.Log
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimberLogger @Inject constructor() : Logger {

    override fun d(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Timber.tag(tag).d(throwable, message)
        } else {
            Timber.tag(tag).d(message)
        }
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Timber.tag(tag).e(throwable, message)
        } else {
            Timber.tag(tag).e(message)
        }
    }

    override fun i(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Timber.tag(tag).i(throwable, message)
        } else {
            Timber.tag(tag).i(message)
        }
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Timber.tag(tag).w(throwable, message)
        } else {
            Timber.tag(tag).w(message)
        }
    }
}