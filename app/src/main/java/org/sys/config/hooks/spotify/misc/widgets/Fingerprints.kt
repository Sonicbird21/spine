package org.sys.config.hooks.spotify.misc.widgets

import org.sys.config.core.dexkit.Opcode
import org.sys.config.core.dexkit.findMethodDirect
import org.sys.config.core.dexkit.fingerprint

val canBindAppWidgetPermissionFingerprint = findMethodDirect {
    fingerprint {
        strings("android.permission.BIND_APPWIDGET")
        opcodes(Opcode.AND_INT_LIT8)
    }
}
