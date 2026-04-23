package com.spotify.music.hooks.spotify.misc.widgets

import com.spotify.music.core.dexkit.Opcode
import com.spotify.music.core.dexkit.findMethodDirect
import com.spotify.music.core.dexkit.fingerprint

val canBindAppWidgetPermissionFingerprint = findMethodDirect {
    fingerprint {
        strings("android.permission.BIND_APPWIDGET")
        opcodes(Opcode.AND_INT_LIT8)
    }
}
