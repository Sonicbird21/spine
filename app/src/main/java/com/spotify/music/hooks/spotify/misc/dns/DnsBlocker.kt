package com.spotify.music.hooks.spotify.misc.dns

internal object DnsBlocker {
    fun shouldBlock(hostname: String?): Boolean {

        if (hostname.isNullOrBlank()) return false
        val host = hostname.trimEnd('.').lowercase()

        if (!host.endsWith(".spotify.com")) return false
        val labels = host.split('.')

        if (labels.size < 4) return false
        if (labels[labels.size - 2] != "spotify" || labels.last() != "com") return false

        val dealerLabelIndex = labels.size - 4
        val dealerLabel = labels[dealerLabelIndex]

        if (!dealerLabel.endsWith("-dealer")) return false
        if (dealerLabel.length <= "-dealer".length) return false

        return true
    }
}
