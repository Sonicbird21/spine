package org.sys.config.core.utils

import android.app.Application
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

fun attachApplicationContext(lpparam: LoadPackageParam, onReady: (Application) -> Unit) {
    val appClassName = lpparam.appInfo.className ?: "android.app.Application"
    val appClass = XposedHelpers.findClass(appClassName, lpparam.classLoader)

    XposedBridge.hookMethod(appClass.getMethod("onCreate"), object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            onReady(param.thisObject as Application)
        }
    })
}
