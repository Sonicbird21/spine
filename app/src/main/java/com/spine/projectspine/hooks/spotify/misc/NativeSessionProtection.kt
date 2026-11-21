package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.utils.Logger
import kotlin.concurrent.thread

object NativeSessionProtection {
    @Volatile private var nativeLoaded = false

    fun install() {
        try {
            System.loadLibrary("spinenative")
            nativeLoaded = true
        } catch (t: Throwable) {
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

    fun clientTokenReady(): Boolean =
        if (!nativeLoaded) true else runCatching { isClientTokenReady() }.getOrDefault(true)

    private external fun tryInstall(): Boolean
    private external fun isClientTokenReady(): Boolean
}
