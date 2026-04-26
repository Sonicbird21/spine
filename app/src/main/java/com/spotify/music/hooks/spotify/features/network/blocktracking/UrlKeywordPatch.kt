package com.spotify.music.hooks.spotify.features.network.blocktracking

object UrlKeywordPatch {
    fun shouldBlock(url: String, keywords: List<String>): Boolean {
        return keywords.any { keyword ->
            url.contains(keyword, ignoreCase = true)
        }
    }
}
