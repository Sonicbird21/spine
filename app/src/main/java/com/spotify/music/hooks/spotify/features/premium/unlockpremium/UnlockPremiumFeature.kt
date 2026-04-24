package com.spotify.music.hooks.spotify.features.premium.unlockpremium

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.feature.HookFeature

class UnlockPremiumFeature : HookFeature {
    override val id: String = "UnlockPremium"

    override fun install(context: FeatureContext) {
        with(context.baseHook) {
            unlockPremium()
        }
    }
}
