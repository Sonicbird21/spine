package com.spotify.music.hooks.spotify.features.network.clienttoken

import com.spotify.music.core.utils.Logger

object SpoofClientPatch {
    @Volatile
    private var listener: RequestListener? = null

    @Synchronized
    fun launchListener(port: Int) {
        if (listener != null) {
            Logger.info("Listener already running on port $port")
            return
        }

        try {
            Logger.info("Launching listener on port $port")
            listener = RequestListener(port)
        } catch (ex: Exception) {
            Logger.error("launchListener failure", ex)
            listener = null
        }
    }
}