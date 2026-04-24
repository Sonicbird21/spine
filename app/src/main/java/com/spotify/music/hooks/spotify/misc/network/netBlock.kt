package com.spotify.music.hooks.spotify.misc.network

import com.spotify.music.core.hook.BaseHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
 

private val blockedUrlKeywords = listOf(
    "ads",
    "tracking",
)


private fun shouldBlockUrl(url: String): Boolean {
    return blockedUrlKeywords.any { keyword ->
        url.contains(keyword, ignoreCase = true)
    }
}


fun blockTracking() {
    runCatching {
        val cl = BaseHook.instance.classLoader
        val httpConnection = cl.loadClass("com.spotify.core.http.NativeHttpConnection")
        val httpRequest = cl.loadClass("com.spotify.core.http.HttpRequest")
        val urlField = httpRequest.getDeclaredField("url")
        urlField.isAccessible = true

        XposedBridge.hookAllMethods(
            httpConnection,
            "send",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val request = param.args[0]
                    val url = urlField.get(request) as? String ?: return

                    if (shouldBlockUrl(url)) {
                        XposedBridge.log("[netBlock] blocked request: $url")
                        param.result = null
                    }
                }
            }
        )
    }.onFailure { XposedBridge.log("netBlock failed to load: ${it.message}") }
}