package com.spotify.music.hooks.spotify.features.network.blocktracking

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import com.spotify.music.hooks.spotify.features.network.session.SessionState

class HttpSendHook(
    private val blockedKeywords: List<String>,
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
                    val url = urlField.get(request) as? String ?: return

                    if (url.contains("ad-logic/state/config", ignoreCase = true)) {
                        SessionState.isAuthenticatedSession = true
                        Logger.info("[Auth] Spotify authenticated.")
                    }

                    if (!UrlKeywordPatch.shouldBlock(url, blockedKeywords)) return

                    Logger.info("[BlockTracking] blocked request: $url")
                    param.result = null
                }
            }
        )
    }
}
