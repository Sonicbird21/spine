package com.spotify.music.hooks.spotify.features.logging.internal

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.feature.HookFeature
import com.spotify.music.core.utils.Logger

data class InternalLoggerConfig(
    val enabled: Boolean = false,
    val verbosity: InternalLoggerVerbosity = InternalLoggerVerbosity.NORMAL,
)

enum class InternalLoggerVerbosity {
    LOW,
    NORMAL,
    HIGH,
    FULL,
}

class InternalLoggerFeature(
    private val config: InternalLoggerConfig = InternalLoggerConfig(),
) : HookFeature {
    override val id: String = "InternalLogger"

    override fun install(context: FeatureContext) {
        InternalLoggerHook.isEnabled = config.enabled
        InternalLoggerHook.verbosity = config.verbosity
        if (!config.enabled) {
            Logger.info("[InternalLogger] disabled by config")
            return
        }
        Logger.info("[InternalLogger] enabled with verbosity=${config.verbosity}")
        InternalLoggerHook().install(context)
    }
}
