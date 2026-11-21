package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.hook.BaseHook
import com.spine.projectspine.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

// just for debug

fun BaseHook.logPlayerErrors() {
    val clazz = XposedHelpers.findClass(
        "com.spotify.player.model.AutoValue_PlayerError",
        classLoader,
    )
    XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            runCatching {
                val obj = param.thisObject
                val error = XposedHelpers.callMethod(obj, "error")?.toString() ?: return
                if (error == "SUCCESS") return
                val reasons = XposedHelpers.callMethod(obj, "reasons")
                val trackUri = XposedHelpers.callMethod(obj, "trackUri")
                val contextUri = XposedHelpers.callMethod(obj, "contextUri")
                Logger.info("[PlayerError] error=$error reasons=$reasons track=$trackUri context=$contextUri")
            }
        }
    })
}
