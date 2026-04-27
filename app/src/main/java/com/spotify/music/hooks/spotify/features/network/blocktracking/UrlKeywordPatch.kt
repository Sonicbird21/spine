package com.spotify.music.hooks.spotify.features.network.blocktracking

object UrlKeywordPatch {
    fun shouldBlock(url: String, keywords: List<String>): Boolean {
        val normalizedUrl = normalizePath(url)

        return keywords.any { keyword ->
            val normalizedKeyword = normalizePath(keyword)
            normalizedUrl.contains(normalizedKeyword) ||
                normalizedUrl.contains(stripPublicSegment(normalizedKeyword))
        }
    }

    private fun normalizePath(value: String): String {
        return value
            .substringBefore('?')
            .substringBefore('#')
            .replace(Regex("/+"), "/")
            .trim('/')
            .lowercase()
    }

    private fun stripPublicSegment(value: String): String {
        return value.replace("/public/", "/")
    }
}
