package org.sys.config.hooks.spotify.misc

import org.sys.config.core.dexkit.AccessFlags
import org.sys.config.core.dexkit.Opcode
import org.sys.config.core.dexkit.findClassDirect
import org.sys.config.core.dexkit.findFieldDirect
import org.sys.config.core.dexkit.findMethodDirect
import org.sys.config.core.dexkit.fingerprint
import org.sys.config.core.dexkit.strings
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

val buildQueryParametersFingerprint = findMethodDirect {
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

val contextFromJsonFingerprint = findMethodDirect {
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
    structureGetSectionsFingerprint("homeapi.proto.HomeStructure")
val browseStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("browsita.v1.resolved.BrowseStructure")
val npvScrollStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("scrollsita.v1.NpvScrollStructure")

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
