package com.spotify.music.core.utils

import com.spotify.music.BuildConfig
import de.robv.android.xposed.XposedBridge

/**
 * Logger that writes to LSPosed bridge and console.
 * Automatically tags all logs with the module name for easy filtering.
 */
object Logger {
    private const val TAG = "SysConfig"

    fun debug(message: String) {
        log("D", message)
    }

    fun info(message: String) {
        log("I", message)
    }

    fun infoDebugOnly(message: String) {
        if (BuildConfig.DEBUG) {
            info(message)
        }
    }

    fun warn(message: String) {
        log("W", message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            log("E", "$message\n${throwable.stackTraceToString()}")
        } else {
            log("E", message)
        }
    }

    fun verbose(message: String) {
        log("V", message)
    }

    private fun log(level: String, message: String) {
        val logMessage = "[$TAG] $message"
        
        // Log to Xposed (LSPosed bridge)
        when (level) {
            "D" -> XposedBridge.log(logMessage)
            "I" -> XposedBridge.log(logMessage)
            "W" -> XposedBridge.log("WARNING: $logMessage")
            "E" -> XposedBridge.log("ERROR: $logMessage")
            "V" -> XposedBridge.log(logMessage)
        }
        
        // Also log to Android Log for debugging
        try {
            val logClass = Class.forName("android.util.Log")
            val method = logClass.getMethod(
                "println",
                Int::class.java,
                String::class.java,
                String::class.java
            )
            val levelValue = when (level) {
                "D" -> 3 // Log.DEBUG
                "I" -> 4 // Log.INFO
                "W" -> 5 // Log.WARN
                "E" -> 6 // Log.ERROR
                "V" -> 2 // Log.VERBOSE
                else -> 4
            }
            method.invoke(null, levelValue, TAG, message)
        } catch (e: Exception) {
            // Fallback to System.out if Log fails
            System.out.println("[$TAG] $level: $message")
        }
    }
}
