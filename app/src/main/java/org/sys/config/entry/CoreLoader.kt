package org.sys.config.entry

import android.app.Application
import android.widget.Toast
import org.sys.config.core.hook.BaseHook
import org.sys.config.core.utils.Logger
import org.sys.config.core.utils.attachApplicationContext
import org.sys.config.hooks.spotify.SpotifyHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.IXposedHookZygoteInit.StartupParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

// LSPosed entry point. Add target packages and their corresponding hook classes below.
class CoreLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private var selectedPackage: String? = null

    private val factories: Map<String, (Application, LoadPackageParam) -> BaseHook> = mapOf(
        "com.spotify.music" to { app, lpparam -> SpotifyHook(app, lpparam) },
    )

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (!lpparam.isFirstApplication) return
        if (!shouldHandle(lpparam.packageName)) return

        Logger.info("handleLoadPackage: ${lpparam.packageName}")

        attachApplicationContext(lpparam) { app ->
            Logger.info("Application context attached for ${lpparam.packageName}")
            runCatching {
                factories[lpparam.packageName]?.invoke(app, lpparam)?.Hook()
            }.onFailure { err ->
                Logger.error("Failed to initialize hooks for ${lpparam.packageName}", err)
            }
        }
    }

    override fun initZygote(startupParam: StartupParam) = Unit

    private fun shouldHandle(packageName: String): Boolean {
        if (packageName !in factories) return false
        if (selectedPackage == null) selectedPackage = packageName
        return selectedPackage == packageName
    }
}
