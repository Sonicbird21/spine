package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.hook.BaseHook
import com.spine.projectspine.core.utils.Logger
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

fun BaseHook.bypassRootCheck() {
    val clazz = runCatching {
        XposedHelpers.findClass("com.ravelin.core.util.security.RootCheckerNative", classLoader)
    }.getOrElse {
        Logger.warn("RootCheckerNative not found; skipping root bypass")
        return
    }

    val returnFalse = XC_MethodReplacement.returnConstant(false)
    val targets = setOf("checkForRoot", "isMagiskPresent")
    val hooked = clazz.declaredMethods
        .filter { it.name in targets }
        .onEach { method ->
            runCatching { XposedBridge.hookMethod(method, returnFalse) }
                .onSuccess { Logger.info("Root bypass: ${method.name} -> false") }
                .onFailure { Logger.warn("Root bypass hook ${method.name} failed: ${it.message}") }
        }

    if (hooked.isEmpty()) Logger.warn("Root bypass: no RootCheckerNative detection methods found")
}
