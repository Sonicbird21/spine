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

val productStateParseFingerprint = findMethodDirect {
    fingerprint {
        classMatcher { descriptor = "Lcom/spotify/remoteconfig/internal/ProductStateProto;" }
        returns("Lcom/spotify/remoteconfig/internal/ProductStateProto;")
        parameters("[B")
    }
}

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
