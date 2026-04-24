package com.spotify.music.hooks.spotify.features.premium.unlockpremium.fingerprints

import com.spotify.music.core.dexkit.AccessFlags
import com.spotify.music.core.dexkit.findClassDirect
import com.spotify.music.core.dexkit.fingerprint

val contextMenuViewModelClassFingerprint = findClassDirect {
    runCatching {
        fingerprint {
            strings("ContextMenuViewModel(header=")
        }
    }.getOrElse {
        fingerprint {
            accessFlags(AccessFlags.CONSTRUCTOR)
            strings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")
            parameters("L", "Ljava/util/List;", "Z")
        }
    }.declaredClass!!
}
