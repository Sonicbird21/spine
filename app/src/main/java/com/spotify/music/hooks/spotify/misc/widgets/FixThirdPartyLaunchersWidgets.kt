package com.spotify.music.hooks.spotify.misc.widgets

import com.spotify.music.core.hook.BaseHook
import de.robv.android.xposed.XC_MethodReplacement

fun BaseHook.fixThirdPartyLaunchersWidgets() {
    ::canBindAppWidgetPermissionFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
}
