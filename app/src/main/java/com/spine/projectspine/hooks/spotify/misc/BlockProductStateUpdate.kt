package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.hook.BaseHook
import com.spine.projectspine.core.utils.Logger
import de.robv.android.xposed.XposedHelpers

// blocks product-state-updates that can lead to token refresh
fun BaseHook.blockProductStateUpdatePush() {
    val observableClass = runCatching {
        XposedHelpers.findClass("io.reactivex.rxjava3.core.Observable", classLoader)
    }.getOrElse {
        Logger.warn("rxjava3 Observable not found; product-state-update block skipped")
        return
    }
    ::productStateUpdateSubscribeFingerprint.hookMethod {
        after { param ->
            if (param.args.getOrNull(0) == "ap://product-state-update") {
                param.result = XposedHelpers.callStaticMethod(observableClass, "never")
                Logger.info("Blocked dealer push: ap://product-state-update")
            }
        }
    }
}
