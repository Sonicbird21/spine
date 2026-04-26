package com.spotify.music.hooks.spotify.features.network.clienttoken

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

class ClientTokenRedirectHook(
    private val port: Int,
) {
    fun install(context: FeatureContext) {
        val httpConnectionClass = context.classLoader.loadClass("com.spotify.core.http.NativeHttpConnection")
        val httpRequestClass = context.classLoader.loadClass("com.spotify.core.http.HttpRequest")
        val urlField = httpRequestClass.getDeclaredField("url").apply { isAccessible = true }

        XposedBridge.hookAllMethods(
            httpConnectionClass,
            "send",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val request = param.args.getOrNull(0) ?: return
                    val originalUrl = urlField.get(request) as? String ?: return

                    val isClientTokenHost = originalUrl.contains("clienttoken.spotify.com", ignoreCase = true)
                    val isClientTokenPath = originalUrl.contains(Constants.CLIENT_TOKEN_API_PATH, ignoreCase = true)
                    if (!isClientTokenHost || !isClientTokenPath) return

                    val redirectedUrl = "http://127.0.0.1:$port${Constants.CLIENT_TOKEN_API_PATH}"
                    if (originalUrl == redirectedUrl) return

                    urlField.set(request, redirectedUrl)
                    Logger.info("[ClientToken] Redirected $originalUrl -> $redirectedUrl")
                }
            }
        )
    }
}