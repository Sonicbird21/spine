package com.spotify.music.hooks.spotify.features.premium.unlockpremium.fingerprints

import com.spotify.music.core.dexkit.findMethodDirect
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.MethodData

val pendragonFetchMessageRequestApplyMethodFingerprint = findMethodDirect {
    var result: MethodData? = null
    withBridge { bridge ->
        result = bridge.findMethod {
            matcher {
                name("apply")
                addInvoke {
                    name("<init>")
                    declaredClass("FetchMessageRequest", StringMatchType.EndsWith)
                }
            }
        }.single()
    }
    requireNotNull(result)
}

val pendragonFetchMessageListRequestApplyMethodFingerprint = findMethodDirect {
    var result: MethodData? = null
    withBridge { bridge ->
        result = bridge.findMethod {
            matcher {
                name("apply")
                addInvoke {
                    name("<init>")
                    declaredClass("FetchMessageListRequest", StringMatchType.EndsWith)
                }
            }
        }.single()
    }
    requireNotNull(result)
}
