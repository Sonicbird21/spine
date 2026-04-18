package com.spine.projectspine.hooks.spotify.misc.widgets

import com.spine.projectspine.core.dexkit.Opcode
import com.spine.projectspine.core.dexkit.findMethodDirect
import com.spine.projectspine.core.dexkit.fingerprint

val canBindAppWidgetPermissionFingerprint = findMethodDirect {
    fingerprint {
        strings("android.permission.BIND_APPWIDGET")
        opcodes(Opcode.AND_INT_LIT8)
    }
}
