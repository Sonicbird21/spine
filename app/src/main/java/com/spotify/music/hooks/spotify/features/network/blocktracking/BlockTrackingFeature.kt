package com.spotify.music.hooks.spotify.features.network.blocktracking

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.feature.HookFeature

data class BlockTrackingConfig(
    val blockedKeywords: List<String> = listOf(
        "ads",
        "tracking",
    ),
)

class BlockTrackingFeature(
    private val config: BlockTrackingConfig = BlockTrackingConfig(),
) : HookFeature {
    override val id: String = "BlockTracking"

    override fun install(context: FeatureContext) {
        HttpSendHook(config.blockedKeywords).install(context)
    }
}
