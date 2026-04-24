package com.spotify.music.hooks.spotify.features.premium.unlockpremium.fingerprints

import com.spotify.music.core.dexkit.Opcode
import com.spotify.music.core.dexkit.findMethodDirect
import com.spotify.music.core.dexkit.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

val contextFromJsonMethodFingerprint = findMethodDirect {
    fingerprint {
        opcodes(
            Opcode.INVOKE_STATIC,
            Opcode.MOVE_RESULT_OBJECT,
            Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT_OBJECT,
            Opcode.INVOKE_STATIC,
        )
        methodMatcher {
            name = "fromJson"
            declaredClass("voiceassistants.playermodels.ContextJsonAdapter", StringMatchType.EndsWith)
        }
    }
}
