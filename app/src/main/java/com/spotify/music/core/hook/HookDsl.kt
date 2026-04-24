package com.spotify.music.core.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

typealias HookCallback = (XC_MethodHook.MethodHookParam) -> Unit
typealias ScopedHookCallback = ScopedHookParam.(XC_MethodHook.MethodHookParam) -> Unit

class HookDsl<TCallback>(emptyCallback: TCallback) {
    var before: TCallback = emptyCallback
    var after: TCallback = emptyCallback

    fun before(f: TCallback) {
        before = f
    }

    fun after(f: TCallback) {
        after = f
    }
}

inline fun Member.hookMethod(crossinline block: HookDsl<HookCallback>.() -> Unit) {
    val builder = HookDsl<HookCallback> {}.apply(block)
    XposedBridge.hookMethod(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            builder.before(param)
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            builder.after(param)
        }
    })
}

@JvmInline
value class ScopedHookParam(val outerParam: XC_MethodHook.MethodHookParam)

class ScopedHook : XC_MethodHook() {
    private val outer = ThreadLocal<XC_MethodHook.MethodHookParam>()

    override fun beforeHookedMethod(param: MethodHookParam) {
        outer.set(param)
    }

    override fun afterHookedMethod(param: MethodHookParam) {
        outer.remove()
    }

    fun hookInnerMethod(
        hookMethod: Member,
        before: ScopedHookCallback,
        after: ScopedHookCallback
    ) {
        XposedBridge.hookMethod(hookMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val outerParam = outer.get() ?: return
                before(ScopedHookParam(outerParam), param)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val outerParam = outer.get() ?: return
                after(ScopedHookParam(outerParam), param)
            }
        })
    }
}

fun scopedHook(vararg pairs: Pair<Member, HookDsl<ScopedHookCallback>.() -> Unit>): XC_MethodHook {
    val hook = ScopedHook()
    pairs.forEach { (member, block) ->
        val builder = HookDsl<ScopedHookCallback> {}.apply(block)
        hook.hookInnerMethod(member, builder.before, builder.after)
    }
    return hook
}

inline fun scopedHook(
    hookMethod: Member,
    crossinline block: HookDsl<ScopedHookCallback>.() -> Unit
): XC_MethodHook {
    val hook = ScopedHook()
    val builder = HookDsl<ScopedHookCallback> {}.apply(block)
    hook.hookInnerMethod(hookMethod, builder.before, builder.after)
    return hook
}
