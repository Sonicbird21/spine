package com.spotify.music.hooks.spotify.features.network.clienttoken

import android.app.Application
import android.os.Build
import com.spotify.music.BuildConfig
import java.util.Locale

object Constants {
    const val CLIENT_TOKEN_API_PATH = "/v1/clienttoken"
    const val CLIENT_TOKEN_API_URL = "https://clienttoken.spotify.com$CLIENT_TOKEN_API_PATH"

    fun getClientVersion(): String {
        val application = currentApplication() ?: return BuildConfig.VERSION_NAME
        return runCatching {
            val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
            packageInfo.versionName?.takeIf { it.isNotBlank() } ?: packageInfo.longVersionCode.toString()
        }.getOrDefault(BuildConfig.VERSION_NAME)
    }

    fun getSystemVersion(): String {
        return Build.VERSION.RELEASE?.takeIf { it.isNotBlank() }
            ?: Build.VERSION.SDK_INT.toString()
    }

    fun getHardwareMachine(): String {
        return listOf(Build.DEVICE, Build.MODEL, Build.HARDWARE)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?: String.format(Locale.US, "%s", Build.UNKNOWN)
    }

    private fun currentApplication(): Application? {
        return runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Application
        }.getOrNull()
    }
}