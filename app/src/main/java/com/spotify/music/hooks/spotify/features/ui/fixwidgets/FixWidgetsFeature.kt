package com.spotify.music.hooks.spotify.features.ui.fixwidgets

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.feature.HookFeature

class FixWidgetsFeature : HookFeature {
    override val id: String = "FixWidgets"

    override fun install(context: FeatureContext) {
        with(context.baseHook) {
            fixWidgets()
        }
    }
}
