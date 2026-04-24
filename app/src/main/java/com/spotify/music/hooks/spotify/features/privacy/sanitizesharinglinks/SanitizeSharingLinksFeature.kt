package com.spotify.music.hooks.spotify.features.privacy.sanitizesharinglinks

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.feature.HookFeature

class SanitizeSharingLinksFeature : HookFeature {
    override val id: String = "SanitizeSharingLinks"

    override fun install(context: FeatureContext) {
        with(context.baseHook) {
            sanitizeSharingLinks()
        }
    }
}
