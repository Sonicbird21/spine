package com.spotify.music.core.feature

interface HookFeature {
    val id: String
    val dependsOn: Set<String>
        get() = emptySet()

    fun install(context: FeatureContext)
}
