package com.spotify.music.hooks.spotify.misc.dns

import com.spotify.music.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import com.spotify.music.core.hook.BaseHook
import com.spotify.music.hooks.spotify.SpotifyHook
import java.net.InetAddress
import java.net.UnknownHostException

fun SpotifyHook.dnsBlock() {
    val inetAdressClass = InetAddress::class.java

    XposedBridge.hookMethod(
        XposedHelpers.findMethodExact(
            inetAdressClass,
            "getAllByName",
            String::class.java
        ),
        object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val hostname = param.args.getOrNull(0) as? String
                if (DnsBlocker.shouldBlock(hostname)) {
                    Logger.info("Blocking DNS request for $hostname")
                    param.throwable = UnknownHostException(hostname)
                }
            }
        }

    )

    runCatching {
        val method = XposedHelpers.findMethodExactIfExists(
            inetAdressClass,
            "getAllByNameOnNet",
            String::class.java,
            Int::class.javaPrimitiveType
        )
        if (method != null) {
            XposedBridge.hookMethod(
                method,
                object: XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val hostname = param.args.getOrNull(0) as? String
                        if (DnsBlocker.shouldBlock(hostname)) {
                            Logger.info("Blocking DNS request for $hostname")
                            param.throwable = UnknownHostException(hostname)
                        }
                    }
                }
            )
        } else {
            Logger.warn("Could not find getAllByNameOnNet method, skipping hook")
        }
    }
}