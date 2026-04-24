package com.spotify.music.hooks.spotify.features.network.blockdealer

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class DealerPubSubHook {
    fun install(context: FeatureContext) {
        with(context.baseHook) {
            Logger.info("[BlockDealer] resolving PubSub subscribe fingerprint...")
            val internalSubscribeMember = runCatching { ::dealerPubSubSubscribeMethodFingerprint.member }.getOrElse { err ->
                Logger.error("[BlockDealer] failed to resolve PubSub subscribe method fingerprint", err)
                return
            }
            Logger.info(
                "[BlockDealer] fingerprint resolved to ${internalSubscribeMember.declaringClass.name}#${internalSubscribeMember.name}"
            )

            val blockingSubscribeMethod = resolveObservableSubscribeMethod(internalSubscribeMember as Method)
            if (blockingSubscribeMethod == null) {
                Logger.error("[BlockDealer] failed to resolve observable PubSub subscribe method from fingerprint class")
                return
            }
            Logger.info(
                "[BlockDealer] blocking hook target resolved to ${blockingSubscribeMethod.declaringClass.name}#${blockingSubscribeMethod.name}"
            )

            XposedBridge.hookMethod(
                blockingSubscribeMethod,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val uri = param.args.getOrNull(0) as? String ?: return
                        if (uri.equals("client:logout", ignoreCase = true)) {
                            val observableClass = (param.method as? Method)?.returnType ?: return
                            val neverMethod = runCatching { observableClass.getMethod("never") }.getOrNull()
                            if (neverMethod == null) {
                                Logger.warn("[BlockDealer] BLOCK FAILED uri=$uri reason=no never() on ${observableClass.name}")
                                return
                            }
                            param.result = neverMethod.invoke(null)
                            Logger.info("[BlockDealer] BLOCK APPLIED uri=$uri")
                            return
                        }
                    }
                }
            )

            XposedBridge.hookMethod(
                internalSubscribeMember,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val uri = param.args.getOrNull(0) as? String ?: return
                        if (!uri.equals("ap://product-state-update", ignoreCase = true)) return

                        val upstream = param.result ?: return
                        val doOnNextMethod = findInstanceMethod(upstream.javaClass, "doOnNext") ?: return
                        val consumerType = doOnNextMethod.parameterTypes.firstOrNull() ?: return
                        val consumer = Proxy.newProxyInstance(
                            context.classLoader,
                            arrayOf(consumerType)
                        ) { _, method, args ->
                            if (method.name == "accept") {
                                // future logging 
                                args?.getOrNull(0)
                            }
                            null
                        }

                        param.result = doOnNextMethod.invoke(upstream, consumer)
                    }
                }
            )
        }
    }

    private fun resolveObservableSubscribeMethod(anchorMethod: Method): Method? {
        val targetClass = anchorMethod.declaringClass
        val anchorParams = anchorMethod.parameterTypes
        val expectedSecondParam = anchorParams.getOrNull(1) ?: return null
        return targetClass.declaredMethods.firstOrNull { method ->
            method.parameterCount == 2 &&
                method.parameterTypes.getOrNull(0) == String::class.java &&
                method.parameterTypes.getOrNull(1) == expectedSecondParam &&
                hasNeverFactory(method.returnType)
        }?.apply {
            isAccessible = true
        }
    }

    private fun hasNeverFactory(type: Class<*>): Boolean {
        return runCatching { type.getMethod("never") }.getOrNull() != null
    }

    private fun findInstanceMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { method ->
                method.name == name && method.parameterCount == 1
            }?.let { method ->
                method.isAccessible = true
                return method
            }
            current = current.superclass
        }
        return null
    }
}
