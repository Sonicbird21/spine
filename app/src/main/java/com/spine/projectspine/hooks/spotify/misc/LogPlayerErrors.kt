package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.hook.BaseHook
import com.spine.projectspine.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

// Diagnostic: log every non-SUCCESS PlayerError the player builds, so a skip/stop
// prints its real reason to logcat (e.g. PLAY_TOKEN_LOST, LICENSE_CHANGE,
// PLAYBACK_ERROR, TRACK_UNAVAILABLE_OFFLINE, ONE_TRACK_UNPLAYABLE). Spotify's own
// native logs never reach logcat, so this is how we see why playback breaks.
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
