package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.hook.BaseHook
import com.spine.projectspine.core.utils.Logger
import java.util.concurrent.atomic.AtomicReference

// Keeps the client-token flowing through the transient (re)auth windows that otherwise wedge
// playback until relogin. Spotify's HTTP interceptor asks k1c1.a() for the client-token on
// every request; it returns a sealed result whose SUCCESS type carries the token string plus
// two `long` expiry timestamps, and whose FAILURE type carries only an int code + message.
// During a restart/reconnect the acquire can fail, so a() returns the failure -> the
// interceptor short-circuits the request to HTTP 503 -> audio-key/CDN fail -> drm_denied ->
// skip, and (on INVALID/MISSING/UNSUPPORTED) the interceptor latches that failure for every
// later request until a fresh login rebuilds it. That is the restart wedge.
//
// Fix: cache the last successful token and, when a() would return a failure, hand back the
// last good token instead. Requests then carry a real (if slightly stale) client-token through
// the bad window. If the cached token is genuinely expired, Spotify's own EXPIRED_CLIENTTOKEN
// path refreshes and retries once (no loop), so this never does worse than the 503 it replaces.
//
// Version-independent: k1c1.a() is located by dexkit anchored on the stable failure string
// (clientTokenGetTokenFingerprint); success vs failure is told apart structurally (success has
// a `long` field), never by obfuscated class name. Degrades safely if the types ever drift:
// an unrecognized success is simply not cached, a mis-cached failure yields the same 503.
fun BaseHook.stabilizeClientToken() {
    val lastGoodToken = AtomicReference<Any?>(null)

    ::clientTokenGetTokenFingerprint.hookMethod {
        after { param ->
            val result = param.result ?: return@after
            if (isSuccessClientToken(result)) {
                lastGoodToken.set(result)
            } else {
                lastGoodToken.get()?.let { good ->
                    param.result = good
                    Logger.infoDebugOnly("Served last-good client-token through a failure window")
                }
            }
        }
    }
}

// Success (r6h) has the token string + two primitive `long` expiry fields; failure (m6h) is an
// int code + String message with no long. Structural check — no hardcoded class names.
private fun isSuccessClientToken(token: Any): Boolean =
    token.javaClass.declaredFields.any { it.type == Long::class.javaPrimitiveType }
