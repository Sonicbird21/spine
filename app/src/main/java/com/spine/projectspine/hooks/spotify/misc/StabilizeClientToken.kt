package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.hook.BaseHook
import com.spine.projectspine.core.utils.Logger
import java.util.concurrent.atomic.AtomicReference


fun BaseHook.stabilizeClientToken() {
    val lastGoodToken = AtomicReference<Any?>(null)

    ::clientTokenGetTokenFingerprint.hookMethod {
        after { param ->
            val result = param.result ?: return@after
            if (isSuccessClientToken(result)) {
                lastGoodToken.set(result)
            } else {
                lastGoodToken.get()?.let { good ->
                    param.result = good
                    Logger.infoDebugOnly("Served last-good client-token through a failure window")
                }
            }
        }
    }
}

private fun isSuccessClientToken(token: Any): Boolean =
    token.javaClass.declaredFields.any { it.type == Long::class.javaPrimitiveType }
