package com.spotify.music.hooks.spotify.features.premium.unlockpremium.fingerprints

import com.spotify.music.core.dexkit.findMethodDirect
import com.spotify.music.core.dexkit.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.enums.UsingType

private fun structureGetSectionsMethodFingerprint(className: String) = findMethodDirect {
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

val homeStructureGetSectionsMethodFingerprint =
    structureGetSectionsMethodFingerprint("homeapi.proto.HomeStructure")
val browseStructureGetSectionsMethodFingerprint =
    structureGetSectionsMethodFingerprint("browsita.v1.resolved.BrowseStructure")
val npvScrollStructureGetSectionsMethodFingerprint =
    structureGetSectionsMethodFingerprint("scrollsita.v1.NpvScrollStructure")
