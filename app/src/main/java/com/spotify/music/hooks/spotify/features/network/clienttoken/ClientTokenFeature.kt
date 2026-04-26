package com.spotify.music.hooks.spotify.features.network.clienttoken

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.feature.HookFeature

data class ClientTokenConfig(
    val port: Int = 8080,
)

class ClientTokenFeature(
    private val config: ClientTokenConfig = ClientTokenConfig(),
) : HookFeature {
    override val id: String = "ClientToken"

    override fun install(context: FeatureContext) {
        SpoofClientPatch.launchListener(config.port)
        ClientTokenRedirectHook(config.port).install(context)
    }
}