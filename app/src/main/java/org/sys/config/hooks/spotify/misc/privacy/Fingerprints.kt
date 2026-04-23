package org.sys.config.hooks.spotify.misc.privacy

import org.sys.config.core.dexkit.findMethodDirect
import org.sys.config.core.dexkit.fingerprint

val shareCopyUrlFingerprint = findMethodDirect {
    runCatching {
        fingerprint {
            returns("Ljava/lang/Object;")
            parameters("Ljava/lang/Object;")
            strings("clipboard", "Spotify Link")
            methodMatcher { name = "invokeSuspend" }
        }
    }.getOrElse {
        fingerprint {
            returns("Ljava/lang/Object;")
            parameters("Ljava/lang/Object;")
            strings("clipboard", "createNewSession failed")
            methodMatcher { name = "apply" }
        }
    }
}
