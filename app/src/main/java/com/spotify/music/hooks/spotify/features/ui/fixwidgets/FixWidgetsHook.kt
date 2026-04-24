package com.spotify.music.hooks.spotify.features.ui.fixwidgets

import com.spotify.music.core.hook.BaseHook
import de.robv.android.xposed.XC_MethodReplacement

fun BaseHook.fixWidgets() {
    ::CanBindAppWidgetPermissionMethodFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
}
