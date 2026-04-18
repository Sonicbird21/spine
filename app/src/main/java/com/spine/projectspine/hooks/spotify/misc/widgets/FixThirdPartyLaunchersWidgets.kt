package com.spine.projectspine.hooks.spotify.misc.widgets

import com.spine.projectspine.core.hook.BaseHook
import de.robv.android.xposed.XC_MethodReplacement

fun BaseHook.fixThirdPartyLaunchersWidgets() {
    ::canBindAppWidgetPermissionFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
}
