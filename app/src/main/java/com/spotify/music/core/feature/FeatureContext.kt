package com.spotify.music.core.feature

import com.spotify.music.core.hook.BaseHook

class FeatureContext(
    val baseHook: BaseHook,
) {
    val classLoader: ClassLoader
        get() = baseHook.classLoader
}
