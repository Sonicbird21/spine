package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.hook.BaseHook
import com.spine.projectspine.core.utils.Logger
import de.robv.android.xposed.XposedHelpers

// Spotify subscribes to the dealer push `ap://product-state-update` and, on each push,
// re-fetches/re-applies product state from the server. That server-driven refresh brings
// the real (free) state and can re-trigger the "Product state changed -> force renew" churn
// that wedges the client-token. EeveeSpotify drops this push on iOS; we do the Android
// equivalent by returning an Observable that never emits for that URI.
//
// Version-independent: the subscribe method is located by dexkit anchored on the stable URI
// string (productStateUpdateSubscribeFingerprint), not a hardcoded obfuscated class name.
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
