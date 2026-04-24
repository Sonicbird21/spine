package com.spotify.music.hooks.spotify.features.premium.unlockpremium.fingerprints

import com.spotify.music.core.dexkit.findFieldDirect
import com.spotify.music.core.dexkit.findMethodDirect
import com.spotify.music.core.dexkit.fingerprint

val productStateMethodFingerprint = findMethodDirect {
    fingerprint {
        returns("Ljava/util/Map;")
        classMatcher { descriptor = "Lcom/spotify/remoteconfig/internal/ProductStateProto;" }
    }
}

val productStateAttributesMapFieldFingerprint = findFieldDirect {
    productStateMethodFingerprint().usingFields.single().field
}
