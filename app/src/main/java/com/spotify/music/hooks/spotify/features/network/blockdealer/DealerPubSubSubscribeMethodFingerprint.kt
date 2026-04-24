package com.spotify.music.hooks.spotify.features.network.blockdealer

import com.spotify.music.core.dexkit.findMethodDirect
import com.spotify.music.core.dexkit.fingerprint

val dealerPubSubSubscribeMethodFingerprint = findMethodDirect {
    runCatching {
        fingerprint {
            strings(
                "[PubSubClientImpl] getObservableOf called for ident ",
                "client:logout",
                "ap://product-state-update",
            )
        }
    }.getOrElse {
        runCatching {
            fingerprint {
                strings(
                    "[PubSubClientImpl] getObservableOf called for ident ",
                    "client:logout",
                )
            }
        }.getOrElse {
            fingerprint {
                strings(
                    "[PubSubClientImpl] getObservableOf called for ident ",
                )
            }
        }
    }
}
