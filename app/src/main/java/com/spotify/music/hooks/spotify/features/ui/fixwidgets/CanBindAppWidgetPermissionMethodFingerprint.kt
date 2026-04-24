package com.spotify.music.hooks.spotify.features.ui.fixwidgets

import com.spotify.music.core.dexkit.Opcode
import com.spotify.music.core.dexkit.findMethodDirect
import com.spotify.music.core.dexkit.fingerprint

val CanBindAppWidgetPermissionMethodFingerprint = findMethodDirect {
    fingerprint {
        strings("android.permission.BIND_APPWIDGET")
        opcodes(Opcode.AND_INT_LIT8)
    }
}
