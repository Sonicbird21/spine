package com.spotify.music.hooks.spotify.features.network.blockdealer

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.feature.HookFeature

class BlockDealerFeature : HookFeature {
    override val id: String = "BlockDealer"

    override fun install(context: FeatureContext) {
        DealerPubSubHook().install(context)
    }
}
