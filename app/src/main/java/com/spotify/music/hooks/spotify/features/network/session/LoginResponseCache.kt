package com.spotify.music.hooks.spotify.features.network.session

import com.spotify.music.core.utils.Logger

object LoginResponseCache {
    @Volatile
    private var cachedResponse: CachedLoginResponse? = null

    data class CachedLoginResponse(
        val status: Int,
        val url: String,
        val headers: String,
        val body: ByteArray,
    )

    fun cacheResponse(response: CachedLoginResponse) {
        synchronized(this) {
            if (cachedResponse == null) {
                cachedResponse = response
                Logger.info("[LoginResponseCache] Login response cached successfully")
            }
        }
    }

    fun getCachedResponse(): CachedLoginResponse? = cachedResponse

    fun clearCache() {
        synchronized(this) {
            cachedResponse = null
            Logger.info("[LoginResponseCache] Cache cleared")
        }
    }

    fun isCached(): Boolean = cachedResponse != null
}