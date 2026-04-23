package org.sys.config.hooks.spotify.misc.widgets

import org.sys.config.core.hook.BaseHook
import de.robv.android.xposed.XC_MethodReplacement

fun BaseHook.fixThirdPartyLaunchersWidgets() {
    ::canBindAppWidgetPermissionFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
}
