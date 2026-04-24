package com.spotify.music.hooks.spotify.features.network.blockdealer

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.net.InetAddress
import java.net.UnknownHostException

class DnsResolutionHook {
    fun install(context: FeatureContext) {
        val inetAddressClass = InetAddress::class.java

        XposedBridge.hookMethod(
            XposedHelpers.findMethodExact(
                inetAddressClass,
                "getAllByName",
                String::class.java
            ),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val hostname = param.args.getOrNull(0) as? String
                    if (!DealerHostnamePatch.shouldBlock(hostname)) return

                    Logger.info("[BlockDealer] blocked DNS request for $hostname")
                    param.throwable = UnknownHostException(hostname)
                }
            }
        )

        val method = XposedHelpers.findMethodExactIfExists(
            inetAddressClass,
            "getAllByNameOnNet",
            String::class.java,
            Int::class.javaPrimitiveType
        )

        if (method == null) {
            Logger.warn("[BlockDealer] getAllByNameOnNet unavailable, skipping")
            return
        }

        XposedBridge.hookMethod(
            method,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val hostname = param.args.getOrNull(0) as? String
                    if (!DealerHostnamePatch.shouldBlock(hostname)) return

                    Logger.info("[BlockDealer] blocked DNS request for $hostname")
                    param.throwable = UnknownHostException(hostname)
                }
            }
        )
    }
}
