package com.spotify.music.hooks.spotify.features.network.blocktracking

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import com.spotify.music.hooks.spotify.features.network.session.SessionState
import com.spotify.music.hooks.spotify.features.network.session.LoginResponseCache
import java.util.Collections
import java.util.WeakHashMap

class HttpSendHook(
    private val blockedKeywords: List<String>,
) {
    companion object {
        @JvmStatic
        var isLogin5ReplayEnabled: Boolean = false
    }

    fun install(context: FeatureContext) {
        val httpConnectionClass = context.classLoader.loadClass("com.spotify.core.http.NativeHttpConnection")
        val httpRequestClass = context.classLoader.loadClass("com.spotify.core.http.HttpRequest")
        val httpResponseClass = context.classLoader.loadClass("com.spotify.core.http.HttpResponse")
        val onHeadersMethod = httpConnectionClass.getMethod("onHeaders", httpResponseClass)
        val onBytesAvailableMethod = httpConnectionClass.getMethod("onBytesAvailable", ByteArray::class.java, Int::class.javaPrimitiveType)
        val onCompleteMethod = httpConnectionClass.getMethod("onComplete")
        val urlField = httpRequestClass.getDeclaredField("url").apply { isAccessible = true }
        val statusGetter = httpResponseClass.getMethod("getStatus")
        val responseUrlGetter = httpResponseClass.getMethod("getUrl")
        val headersGetter = httpResponseClass.getMethod("getHeaders")
        val responseCtor = httpResponseClass.getConstructor(Int::class.javaPrimitiveType, String::class.java, String::class.java)

        val urlByConnection = Collections.synchronizedMap(WeakHashMap<Any, String>())
        val headerByConnection = Collections.synchronizedMap(WeakHashMap<Any, Any>())
        val chunksByConnection = Collections.synchronizedMap(WeakHashMap<Any, MutableList<ByteArray>>())
        val isReplaying = ThreadLocal.withInitial { false }

        XposedBridge.hookAllMethods(
            httpConnectionClass,
            "send",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isReplaying.get() == true) return
                    val request = param.args.getOrNull(0) ?: return
                    val url = urlField.get(request) as? String ?: return
                    val connection = param.thisObject ?: return
                    urlByConnection[connection] = url
                    val isLoginEndpoint = url.contains("login5.spotify.com/v3/login", ignoreCase = true)

                    if (isLogin5ReplayEnabled && isLoginEndpoint) {
                        val cached = LoginResponseCache.getCachedResponse()
                        if (cached != null) {
                            Logger.info("[BlockTracking] Replaying cached login response body.")
                            val replayResponse = responseCtor.newInstance(cached.status, cached.url, cached.headers)
                            isReplaying.set(true)
                            try {
                                onHeadersMethod.invoke(connection, replayResponse)
                                onBytesAvailableMethod.invoke(connection, cached.body, cached.body.size)
                                onCompleteMethod.invoke(connection)
                            } finally {
                                isReplaying.set(false)
                            }
                            param.result = null
                            return
                        }
                    }

                    // Never block login5 when replay is disabled.
                    if (isLoginEndpoint && !isLogin5ReplayEnabled) return

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

        XposedBridge.hookAllMethods(
            httpConnectionClass,
            "onHeaders",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isReplaying.get() == true) return
                    val connection = param.thisObject ?: return
                    val url = urlByConnection[connection] ?: return
                    if (!url.contains("login5.spotify.com/v3/login", ignoreCase = true)) return

                    val response = param.args.getOrNull(0) ?: return
                    headerByConnection[connection] = response
                    chunksByConnection[connection] = mutableListOf()
                    Logger.info("[BlockTracking] Capturing login headers")
                }
            }
        )

        XposedBridge.hookAllMethods(
            httpConnectionClass,
            "onBytesAvailable",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isReplaying.get() == true) return
                    val connection = param.thisObject ?: return
                    val url = urlByConnection[connection] ?: return
                    if (!url.contains("login5.spotify.com/v3/login", ignoreCase = true)) return

                    val buffer = param.args.getOrNull(0) as? ByteArray ?: return
                    val length = param.args.getOrNull(1) as? Int ?: return
                    if (length <= 0) return

                    val chunks = chunksByConnection.getOrPut(connection) { mutableListOf() }
                    chunks.add(buffer.copyOf(length.coerceAtMost(buffer.size)))
                }
            }
        )

        XposedBridge.hookAllMethods(
            httpConnectionClass,
            "onComplete",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isReplaying.get() == true) return
                    if (LoginResponseCache.isCached()) return

                    val connection = param.thisObject ?: return
                    val url = urlByConnection[connection] ?: return
                    if (!url.contains("login5.spotify.com/v3/login", ignoreCase = true)) return

                    val response = headerByConnection.remove(connection) ?: return
                    val chunks = chunksByConnection.remove(connection) ?: return
                    if (chunks.isEmpty()) return

                    val status = statusGetter.invoke(response) as? Int ?: return
                    val responseUrl = responseUrlGetter.invoke(response) as? String ?: url
                    val headers = headersGetter.invoke(response) as? String ?: ""
                    val totalSize = chunks.sumOf { it.size }
                    val body = ByteArray(totalSize)
                    var offset = 0
                    for (chunk in chunks) {
                        chunk.copyInto(body, destinationOffset = offset)
                        offset += chunk.size
                    }

                    LoginResponseCache.cacheResponse(
                        LoginResponseCache.CachedLoginResponse(
                            status = status,
                            url = responseUrl,
                            headers = headers,
                            body = body,
                        )
                    )
                    Logger.info("[BlockTracking] Cached login body ($totalSize bytes)")
                    urlByConnection.remove(connection)
                }
            }
        )
    }
}
