package com.spotify.music.hooks.spotify.features.network.blocktracking

import com.spotify.music.hooks.spotify.features.network.session.LoginResponseCache

object UrlKeywordPatch {
    fun shouldBlock(url: String, keywords: List<String>): Boolean {

        val isLoginEndpoint = url.contains("login5.spotify.com/v3/login", ignoreCase = true)

        if (isLoginEndpoint) {
            return LoginResponseCache.isCached()
        }

        return keywords.any { keyword ->
            url.contains(keyword, ignoreCase = true)
        }
    }
}
