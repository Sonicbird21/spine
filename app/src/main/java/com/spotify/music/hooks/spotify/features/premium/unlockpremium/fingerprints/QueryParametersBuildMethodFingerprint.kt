package com.spotify.music.hooks.spotify.features.premium.unlockpremium.fingerprints

import com.spotify.music.core.dexkit.findMethodDirect
import com.spotify.music.core.dexkit.strings
import org.luckypray.dexkit.result.MethodData

val queryParametersBuildMethodFingerprint = findMethodDirect {
    var result: MethodData? = null
    withBridge { bridge ->
        result = bridge.findMethod {
            matcher {
                strings("trackRows", "device_type:tablet")
            }
        }.single()
    }
    requireNotNull(result)
}
