package com.spotify.music.hooks.spotify.features.network.blocktracking

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.feature.HookFeature

enum class Login5HandlingMode {
    REPLAY_CACHED_RESPONSE,
    BLOCK_WHEN_AUTHENTICATED,
}

data class BlockTrackingConfig(
    val blockedKeywords: List<String> = listOf(
        "gabo-receiver-service/v3/events",
    ),
    val login5HandlingMode: Login5HandlingMode = Login5HandlingMode.BLOCK_WHEN_AUTHENTICATED,
)

class BlockTrackingFeature(
    private val config: BlockTrackingConfig = BlockTrackingConfig(),
) : HookFeature {
    override val id: String = "BlockTracking"

    override fun install(context: FeatureContext) {
        HttpSendHook(config.blockedKeywords, config.login5HandlingMode).install(context)
    }
}
