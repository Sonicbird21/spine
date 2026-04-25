package com.spotify.music.hooks.spotify.features.logging.internal

import com.spotify.music.core.dexkit.AccessFlags
import com.spotify.music.core.dexkit.findMethodDirect
import com.spotify.music.core.dexkit.fingerprint

val internalLoggerMethodFingerprint = findMethodDirect {
    runCatching {
        fingerprint {
            name("core")
            strings("@core")
            parameters("boolean", "java.lang.String")
            returns("void")
            accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
        }
    }.getOrElse {
        runCatching {
            fingerprint {
                strings("@core")
                accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
            }
        }.getOrElse {
            fingerprint {
                strings("@")
                accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
            }
        }
    }
}
