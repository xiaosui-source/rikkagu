package me.rerere.ai.util

import android.util.Log

object AppLogger {
    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }
    
    fun e(tag: String, msg: String, e: Throwable? = null) {
        Log.e(tag, msg, e)
    }
    
    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
    }
}
