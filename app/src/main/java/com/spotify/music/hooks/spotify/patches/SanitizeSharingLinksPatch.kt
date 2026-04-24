package com.spotify.music.hooks.spotify.patches

import android.net.Uri
import com.spotify.music.core.utils.Logger

object SanitizeSharingLinksPatch {
    private val parametersToRemove = setOf("si", "utm_source")

    fun sanitizeSharingLink(url: String): String {
        return try {
            sanitizeUri(Uri.parse(url)).toString()
        } catch (ex: Exception) {
            Logger.error("sanitizeSharingLink failure: $url", ex)
            url
        }
    }

    private fun sanitizeUri(uri: Uri): Uri {
        return try {
            val builder = uri.buildUpon().clearQuery()
            for (paramName in uri.queryParameterNames) {
                if (!parametersToRemove.contains(paramName)) {
                    uri.getQueryParameters(paramName).forEach { value ->
                        builder.appendQueryParameter(paramName, value)
                    }
                }
            }
            builder.build()
        } catch (ex: Exception) {
            Logger.error("sanitizeUri failure: $uri", ex)
            uri
        }
    }
}
