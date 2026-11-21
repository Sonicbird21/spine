package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.utils.Logger
import kotlin.concurrent.thread

// Loads the module's native lib into the Spotify process and byte-patches
// liborbit-jni-spotify.so to stop server-driven session teardown (permanent OAuth
// error + forced-logout broadcast) that otherwise drops the session mid-stream and
// skips the track. liborbit is loaded by Spotify asynchronously, so poll until the
// mapping appears, then patch once.
object NativeSessionProtection {
    @Volatile private var nativeLoaded = false

    fun install() {
        try {
            System.loadLibrary("spinenative")
            nativeLoaded = true
        } catch (t: Throwable) {
            // arm64-only lib; on any other ABI or load failure the Java hooks still apply.
            Logger.error("loadLibrary spinenative failed; native session protection disabled", t)
            return
        }
        thread(name = "spine-nsp", isDaemon = true) {
            repeat(240) { // ~2 min at 500ms
                if (tryInstall()) return@thread
                Thread.sleep(500)
            }
            Logger.warn("liborbit never appeared; native session protection not installed")
        }
    }

    // Gate for the product-state override: premium is applied only while Spotify's
    // client-token is healthy (see the native ct-gate). Fails open (true) if the native
    // lib isn't loaded, so a native failure never disables premium entirely.
    fun clientTokenReady(): Boolean =
        if (!nativeLoaded) true else runCatching { isClientTokenReady() }.getOrDefault(true)

    private external fun tryInstall(): Boolean
    private external fun isClientTokenReady(): Boolean
}
