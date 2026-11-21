package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.dexkit.AccessFlags
import com.spine.projectspine.core.dexkit.Opcode
import com.spine.projectspine.core.dexkit.findClassDirect
import com.spine.projectspine.core.dexkit.findFieldDirect
import com.spine.projectspine.core.dexkit.findMethodDirect
import com.spine.projectspine.core.dexkit.fingerprint
import com.spine.projectspine.core.dexkit.strings
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.enums.UsingType
import org.luckypray.dexkit.result.MethodData

val productStateProtoFingerprint = findMethodDirect {
    fingerprint {
        returns("Ljava/util/Map;")
        classMatcher { descriptor = "Lcom/spotify/remoteconfig/internal/ProductStateProto;" }
    }
}

val attributesMapField = findFieldDirect {
    productStateProtoFingerprint().usingFields.single().field
}

// The pubsub subscribe `b(String uri, parser) -> Observable`, found version-independently:
// locate the method that uses the stable dealer URI string "ap://product-state-update"
// (the subscriber), then pick the Observable-returning (String, …) method it invokes. That
// invoked method is the pubsub subscribe we hook to drop the server's product-state push.
val productStateUpdateSubscribeFingerprint = findMethodDirect {
    var result: MethodData? = null
    withBridge { bridge ->
        val subscriber = bridge.findMethod {
            matcher { usingStrings(listOf("ap://product-state-update")) }
        }.firstOrNull()
        result = subscriber?.invokes?.firstOrNull { m ->
            val params = m.descriptor.substringAfter("(").substringBefore(")")
            m.descriptor.endsWith("Lio/reactivex/rxjava3/core/Observable;") &&
                params.startsWith("Ljava/lang/String;")
        }
    }
    requireNotNull(result)
}

// Static parse entry: ProductStateProto.r(byte[]) -> ProductStateProto. This is where a
// server product-state update is received, so overriding here spoofs premium at the
// source (once per update) instead of mutating on every read (which flip-flops and churns
// "force renew"). Hooking it coherently makes every received product state premium.
val productStateParseFingerprint = findMethodDirect {
    fingerprint {
        classMatcher { descriptor = "Lcom/spotify/remoteconfig/internal/ProductStateProto;" }
        returns("Lcom/spotify/remoteconfig/internal/ProductStateProto;")
        parameters("[B")
    }
}

// The client-token chokepoint the HTTP interceptor calls for every request (k1c1.a()):
// returns the sealed client-token (success type carries the token + two expiry longs, failure
// type an int code + message). We hook it to serve the last good token through a failure
// window instead of letting the failure wedge playback until relogin. Anchored on the stable
// failure string + zero params: a sibling provider (q6h.b(int)) shares the string but takes an
// int, so paramCount=0 disambiguates. No hardcoded obfuscated names.
val clientTokenGetTokenFingerprint = findMethodDirect {
    fingerprint {
        strings("Client token request interrupted")
        methodMatcher { paramCount = 0 }
    }
}

val contextMenuViewModelClass = findClassDirect {
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

private fun structureGetSectionsFingerprint(className: String) = findMethodDirect {
    fingerprint {
        classMatcher { className(className, StringMatchType.EndsWith) }
        methodMatcher {
            paramCount = 0
            addUsingField {
                usingType = UsingType.Read
                name = "sections_"
            }
        }
    }
}

val homeStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("casita.v1.resolved.HomeStructure")
val browseStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("browsita.v1.resolved.BrowseStructure")
val npvScrollStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("scrollsita.v1.NpvScrollStructure")

// Quasar service registry: dgn.c(name, factory) starts a named daemon, returns null if skipped.
val quasarStartServiceFingerprint = fingerprint {
    strings(" created after shutdown invoked", " already started")
}

val pendragonJsonFetchMessageRequestFingerprint = findMethodDirect {
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

val pendragonJsonFetchMessageListRequestFingerprint = findMethodDirect {
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
