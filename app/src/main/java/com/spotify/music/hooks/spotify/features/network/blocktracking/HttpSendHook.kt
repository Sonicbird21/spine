package com.spotify.music.hooks.spotify.features.network.blocktracking

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import com.spotify.music.hooks.spotify.features.network.session.SessionState
import com.spotify.music.hooks.spotify.features.network.session.LoginResponseCache
import java.util.Collections
import java.util.WeakHashMap
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.io.IOException

class HttpSendHook(
    private val blockedKeywords: List<String>,
    private val login5HandlingMode: Login5HandlingMode,
) {
    fun install(context: FeatureContext) {
        val httpConnectionClass = context.classLoader.loadClass("com.spotify.core.http.NativeHttpConnection")
        val httpRequestClass = context.classLoader.loadClass("com.spotify.core.http.HttpRequest")
        val httpOptionsClass = context.classLoader.loadClass("com.spotify.core.http.HttpOptions")
        val httpResponseClass = context.classLoader.loadClass("com.spotify.core.http.HttpResponse")
        val sendMethod = httpConnectionClass.getMethod("send", httpRequestClass, httpOptionsClass)
        val onHeadersMethod = httpConnectionClass.getMethod("onHeaders", httpResponseClass)
        val onBytesAvailableMethod = httpConnectionClass.getMethod("onBytesAvailable", ByteArray::class.java, Int::class.javaPrimitiveType)
        val onCompleteMethod = httpConnectionClass.getMethod("onComplete")
        val urlGetter = httpRequestClass.getMethod("getUrl")
        val statusGetter = httpResponseClass.getMethod("getStatus")
        val responseUrlGetter = httpResponseClass.getMethod("getUrl")
        val headersGetter = httpResponseClass.getMethod("getHeaders")
        val responseCtor = httpResponseClass.getConstructor(Int::class.javaPrimitiveType, String::class.java, String::class.java)

        val urlByConnection = Collections.synchronizedMap(WeakHashMap<Any, String>())
        val headerByConnection = Collections.synchronizedMap(WeakHashMap<Any, Any>())
        val chunksByConnection = Collections.synchronizedMap(WeakHashMap<Any, MutableList<ByteArray>>())
        val isReplaying = ThreadLocal.withInitial { false }

        XposedBridge.hookMethod(
            sendMethod,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isReplaying.get() == true) return
                    val request = param.args.getOrNull(0) ?: return
                    val url = urlGetter.invoke(request) as? String ?: return
                    val connection = param.thisObject ?: return
                    urlByConnection[connection] = url
                    val isLoginEndpoint = url.contains("login5.spotify.com/v3/login", ignoreCase = true)

                    if (isLoginEndpoint) {
                        when (login5HandlingMode) {
                            Login5HandlingMode.REPLAY_CACHED_RESPONSE -> {
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

                                return
                            }
                            Login5HandlingMode.BLOCK_WHEN_AUTHENTICATED -> {
                                if (!SessionState.isAuthenticatedSession) {
                                    return
                                }

                                Logger.info("[BlockTracking] blocked login5 request after authentication: $url")
                                param.result = null
                                return
                            }
                        }
                    }

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

        installOkHttpLogging(context)

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

    private fun installOkHttpLogging(context: FeatureContext) {
        val requestClass = runCatching { context.classLoader.loadClass("okhttp3.Request") }.getOrElse { err ->
            Logger.warn("[BlockTracking][OkHttp] Request class not found: ${err.message}")
            return
        }
        val callClass = runCatching { context.classLoader.loadClass("okhttp3.Call") }.getOrNull()
        val httpUrlClass = runCatching { context.classLoader.loadClass("okhttp3.HttpUrl") }.getOrNull()
        val requestUrlMethod = resolveNoArgMethodByReturnType(requestClass, httpUrlClass)
        val requestVerbMethod = resolveNoArgMethodByReturnType(requestClass, String::class.java)
        val requestUrlField = resolveFieldByType(requestClass, httpUrlClass)
        val requestVerbField = resolveFieldByType(requestClass, String::class.java)

        if (requestUrlMethod == null && requestUrlField == null) {
            Logger.warn("[BlockTracking][OkHttp] Request URL accessor not found; falling back to toString() parsing")
        }
        if (requestVerbMethod == null && requestVerbField == null) {
            Logger.warn("[BlockTracking][OkHttp] Request method accessor not found; falling back to toString() parsing")
        }

        runCatching {
            val callbackClass = context.classLoader.loadClass("okhttp3.Callback")
            val responseClass = context.classLoader.loadClass("okhttp3.Response")
            val realCallClass = context.classLoader.loadClass("okhttp3.internal.connection.RealCall")

            val requestMethod = resolveNoArgMethodByReturnType(realCallClass, requestClass)
                ?: throw NoSuchMethodException("RealCall request getter not found")

            val executeMethods = realCallClass.declaredMethods
                .filter { method ->
                    method.parameterCount == 0 &&
                        method.returnType == responseClass &&
                        method.declaringClass == realCallClass
                }
                .onEach { it.isAccessible = true }

            val enqueueMethods = realCallClass.declaredMethods
                .filter { method ->
                    method.parameterCount == 1 &&
                        method.parameterTypes[0] == callbackClass &&
                        method.returnType == Void.TYPE &&
                        method.declaringClass == realCallClass
                }
                .onEach { it.isAccessible = true }

            if (executeMethods.isEmpty()) {
                throw NoSuchMethodException("RealCall execute method not found")
            }
            if (enqueueMethods.isEmpty()) {
                throw NoSuchMethodException("RealCall enqueue method not found")
            }

            val callbackFailureMethod = resolveCallbackFailureMethod(callbackClass, callClass)
            val cancelMethod = realCallClass.declaredMethods.firstOrNull { method ->
                method.name == "cancel" && method.parameterCount == 0
            }?.apply {
                isAccessible = true
            }

            executeMethods.forEach { executeMethod ->
                XposedBridge.hookMethod(
                    executeMethod,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val (method, url) = extractRequestFromCall(
                                call = param.thisObject,
                                requestMethod = requestMethod,
                                requestUrlMethod = requestUrlMethod,
                                requestVerbMethod = requestVerbMethod,
                                requestUrlField = requestUrlField,
                                requestVerbField = requestVerbField,
                            ) ?: return

                            if (!UrlKeywordPatch.shouldBlock(url, blockedKeywords)) return

                            runCatching { cancelMethod?.invoke(param.thisObject) }
                            Logger.info("[BlockTracking][OkHttp] blocked request: $method $url")
                            param.throwable = IOException("Blocked by BlockTracking")
                        }
                    }
                )
            }

            enqueueMethods.forEach { enqueueMethod ->
                XposedBridge.hookMethod(
                    enqueueMethod,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val (method, url) = extractRequestFromCall(
                                call = param.thisObject,
                                requestMethod = requestMethod,
                                requestUrlMethod = requestUrlMethod,
                                requestVerbMethod = requestVerbMethod,
                                requestUrlField = requestUrlField,
                                requestVerbField = requestVerbField,
                            ) ?: return

                            if (!UrlKeywordPatch.shouldBlock(url, blockedKeywords)) return

                            runCatching { cancelMethod?.invoke(param.thisObject) }
                            val blockedException = IOException("Blocked by BlockTracking")
                            val callback = param.args.getOrNull(0)
                            val callbackInvoked = if (callback != null && callbackFailureMethod != null) {
                                runCatching {
                                    callbackFailureMethod.invoke(callback, param.thisObject, blockedException)
                                }.isSuccess
                            } else {
                                false
                            }

                            Logger.info("[BlockTracking][OkHttp] blocked request: $method $url")
                            if (!callbackInvoked) {
                                param.throwable = blockedException
                                return
                            }
                            param.result = null
                        }
                    }
                )
            }

            Logger.info(
                "[BlockTracking][OkHttp] RealCall hooks installed reflectively " +
                    "(request=${requestMethod.name}, execute=${executeMethods.joinToString { it.name }}, enqueue=${enqueueMethods.joinToString { it.name }})"
            )
        }.onFailure { err ->
            Logger.warn("[BlockTracking][OkHttp] RealCall hook install failed: ${err.message}")
        }
    }

    private fun extractRequestFromCall(
        call: Any?,
        requestMethod: Method,
        requestUrlMethod: Method?,
        requestVerbMethod: Method?,
        requestUrlField: Field?,
        requestVerbField: Field?,
    ): Pair<String, String>? {
        val callObj = call ?: return null
        val request = runCatching { requestMethod.invoke(callObj) }.getOrNull() ?: return null
        return extractOkHttpRequestInfo(
            request = request,
            requestUrlMethod = requestUrlMethod,
            requestVerbMethod = requestVerbMethod,
            requestUrlField = requestUrlField,
            requestVerbField = requestVerbField,
        )
    }

    private fun extractOkHttpRequestInfo(
        request: Any,
        requestUrlMethod: Method?,
        requestVerbMethod: Method?,
        requestUrlField: Field?,
        requestVerbField: Field?,
    ): Pair<String, String> {
        val methodFromMethod = requestVerbMethod?.let { runCatching { it.invoke(request) as? String }.getOrNull() }
        val urlFromMethod = requestUrlMethod?.let { runCatching { it.invoke(request)?.toString() }.getOrNull() }

        val methodFromField = requestVerbField?.let { runCatching { it.get(request) as? String }.getOrNull() }
        val urlFromField = requestUrlField?.let { runCatching { it.get(request)?.toString() }.getOrNull() }

        val dump = runCatching { request.toString() }.getOrNull()
        val parsedMethod = dump
            ?.let { Regex("method=([^,}]+)").find(it)?.groupValues?.getOrNull(1) }
            ?.trim()
        val parsedUrl = dump
            ?.let { Regex("url=([^,}]+)").find(it)?.groupValues?.getOrNull(1) }
            ?.trim()

        val method = methodFromMethod ?: methodFromField ?: parsedMethod ?: "<unknown>"
        val url = urlFromMethod ?: urlFromField ?: parsedUrl ?: "<unknown>"
        return method to url
    }

    private fun resolveNoArgMethodByReturnType(clazz: Class<*>, returnType: Class<*>?): Method? {
        if (returnType == null) return null

        return clazz.declaredMethods.firstOrNull { method ->
            method.parameterCount == 0 && method.returnType == returnType && method.name != "toString"
        }?.apply {
            isAccessible = true
        }
    }

    private fun resolveFieldByType(clazz: Class<*>, fieldType: Class<*>?): Field? {
        if (fieldType == null) return null

        return clazz.declaredFields.firstOrNull { field ->
            field.type == fieldType
        }?.apply {
            isAccessible = true
        }
    }

    private fun resolveCallbackFailureMethod(callbackClass: Class<*>, callClass: Class<*>?): Method? {
        if (callClass == null) return null

        return callbackClass.declaredMethods.firstOrNull { method ->
            method.parameterCount == 2 &&
                method.parameterTypes[0].isAssignableFrom(callClass) &&
                method.parameterTypes[1].isAssignableFrom(IOException::class.java)
        }?.apply {
            isAccessible = true
        }
    }
}
