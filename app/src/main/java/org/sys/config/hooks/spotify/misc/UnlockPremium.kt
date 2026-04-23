package org.sys.config.hooks.spotify.misc

import org.sys.config.core.hook.BaseHook
import org.sys.config.core.utils.Logger
import org.sys.config.core.utils.callMethod
import org.sys.config.hooks.spotify.patches.UnlockPremiumPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.ArrayList
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

@Suppress("UNCHECKED_CAST")
fun BaseHook.unlockPremium() {
    ::productStateProtoFingerprint.hookMethod {
        val field = ::attributesMapField.field
        before { param ->
            val attributes = field.get(param.thisObject) as? Map<String, *> ?: return@before
            UnlockPremiumPatch.overrideAttributes(attributes)
        }
    }

    ::buildQueryParametersFingerprint.hookMethod {
        after { param ->
            val result = param.result
            val field = "checkDeviceCapability"
            if (result.toString().contains("${field}=")) {
                param.result = XposedBridge.invokeOriginalMethod(
                    param.method,
                    param.thisObject,
                    arrayOf(param.args[0], true),
                )
            }
        }
    }

    ::contextFromJsonFingerprint.hookMethod {
        fun removeStationString(field: Field, obj: Any) {
            field.isAccessible = true
            val original = field.get(obj) as? String ?: return
            field.set(obj, UnlockPremiumPatch.removeStationString(original))
        }

        after { param ->
            val target = param.result ?: return@after
            val clazz = target.javaClass
            removeStationString(clazz.getDeclaredField("uri"), target)
            removeStationString(clazz.getDeclaredField("url"), target)
        }
    }

    XposedHelpers.findAndHookMethod(
        "com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides\$Builder",
        classLoader,
        "build",
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.thisObject.callMethod("shufflingContext", false)
            }
        },
    )

    val contextMenuViewModelClazz = ::contextMenuViewModelClass.clazz
    var runtimeFallbackUpsellField: Field? = null

    fun looksLikeViewModel(value: Any): Boolean {
        if (value.toString().contains("ViewModel(itemResId=")) return true
        val booleanCount = value.javaClass.declaredFields.count {
            it.type == Boolean::class.java || it.type == Boolean::class.javaPrimitiveType
        }
        return booleanCount >= 5
    }

    fun resolveViewModel(item: Any): Any? {
        if (looksLikeViewModel(item)) return item

        runCatching { item.callMethod("getViewModel") }
            .getOrNull()
            ?.let { if (looksLikeViewModel(it)) return it }

        item.javaClass.declaredMethods
            .filter { it.parameterCount == 0 && it.returnType != Void.TYPE }
            .forEach { method ->
                runCatching {
                    method.isAccessible = true
                    method.invoke(item)
                }.getOrNull()?.let { candidate ->
                    if (looksLikeViewModel(candidate)) return candidate
                }
            }

        item.javaClass.declaredFields
            .forEach { field ->
                runCatching {
                    field.isAccessible = true
                    field.get(item)
                }.getOrNull()?.let { candidate ->
                    if (looksLikeViewModel(candidate)) return candidate
                }
            }

        return null
    }

    fun resolveRuntimeUpsellField(vm: Any): Field? {
        runtimeFallbackUpsellField?.let { return it }
        return runCatching {
            val boolFields = vm.javaClass.declaredFields.filter {
                it.type == Boolean::class.java || it.type == Boolean::class.javaPrimitiveType
            }
            if (boolFields.size >= 2) {
                boolFields[1].also {
                    it.isAccessible = true
                    runtimeFallbackUpsellField = it
                }
            } else {
                null
            }
        }.getOrNull()
    }

    fun filterContextMenuItems(original: List<*>): List<*> {
        return original.filter {
            it?.let { item ->
                val vm = resolveViewModel(item) ?: return@let false
                val resolvedField = resolveRuntimeUpsellField(vm)
                if (resolvedField != null) {
                    runCatching { resolvedField.get(vm) == true }.getOrDefault(false)
                } else {
                    // Last-resort fallback when field layout changes: rely on generated ViewModel.toString labels.
                    vm.toString().contains("isPremiumUpsell=true")
                }
            } != true
        }
    }

    XposedBridge.hookAllConstructors(
        contextMenuViewModelClazz,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val parameterTypes = (param.method as Constructor<*>).parameterTypes
                for (i in 0 until param.args.size) {
                    if (!List::class.java.isAssignableFrom(parameterTypes[i])) continue
                    val original = param.args[i] as? List<*> ?: continue
                    val filtered = filterContextMenuItems(original)
                    param.args[i] = if (ArrayList::class.java.isAssignableFrom(parameterTypes[i])) {
                        ArrayList(filtered)
                    } else {
                        filtered
                    }
                }
            }
        },
    )

    val listArgMethods = contextMenuViewModelClazz.declaredMethods.filter { method ->
        method.parameterTypes.any { List::class.java.isAssignableFrom(it) }
    }

    listArgMethods.forEach { method: Method ->
        XposedBridge.hookMethod(
            method,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    for (i in 0 until param.args.size) {
                        if (!List::class.java.isAssignableFrom(method.parameterTypes[i])) continue
                        val original = param.args[i] as? List<*> ?: continue
                        val filtered = filterContextMenuItems(original)
                        param.args[i] = if (ArrayList::class.java.isAssignableFrom(method.parameterTypes[i])) {
                            ArrayList(filtered)
                        } else {
                            filtered
                        }
                    }
                }
            }
        )
    }

    ::homeStructureGetSectionsFingerprint.hookMethod {
        after { param ->
            val sections = param.result
            runCatching {
                var clazz: Class<*>? = sections.javaClass
                var boolField: java.lang.reflect.Field? = null
                while (clazz != null) {
                    boolField = clazz.declaredFields.firstOrNull { it.type == Boolean::class.java }
                    if (boolField != null) break
                    clazz = clazz.superclass
                }
                boolField?.let {
                    it.isAccessible = true
                    it.set(sections, true)
                }
            }
            UnlockPremiumPatch.removeHomeSections(param.result as MutableList<*>)
        }
    }

    ::browseStructureGetSectionsFingerprint.hookMethod {
        after { param ->
            val sections = param.result
            runCatching {
                var clazz: Class<*>? = sections.javaClass
                var boolField: java.lang.reflect.Field? = null
                while (clazz != null) {
                    boolField = clazz.declaredFields.firstOrNull { it.type == Boolean::class.java }
                    if (boolField != null) break
                    clazz = clazz.superclass
                }
                boolField?.let {
                    it.isAccessible = true
                    it.set(sections, true)
                }
            }
            UnlockPremiumPatch.removeBrowseSections(param.result as MutableList<*>)
        }
    }


    ::npvScrollStructureGetSectionsFingerprint.hookMethod {
        after { param ->
            val sections = param.result
            runCatching {
                var clazz: Class<*>? = sections.javaClass
                var boolField: java.lang.reflect.Field? = null
                while (clazz != null) {
                    boolField = clazz.declaredFields.firstOrNull { it.type == Boolean::class.java }
                    if (boolField != null) break
                    clazz = clazz.superclass
                }
                boolField?.let {
                    it.isAccessible = true
                    it.set(sections, true)
                }
            }
            UnlockPremiumPatch.removeNpvSections(param.result as MutableList<*>)
        }
    }



    val replaceFetchRequestSingleWithError = object : XC_MethodHook() {
        val justMethod =
            DexMethod("Lio/reactivex/rxjava3/core/Single;->just(Ljava/lang/Object;)Lio/reactivex/rxjava3/core/Single;").toMethod()

        val onErrorField =
            DexField("Lio/reactivex/rxjava3/internal/operators/single/SingleOnErrorReturn;->b:Lio/reactivex/rxjava3/functions/Function;").toField()

        override fun afterHookedMethod(param: MethodHookParam) {
            val result = param.result ?: return
            if (!result.javaClass.name.endsWith("SingleOnErrorReturn")) return
            val justError = justMethod.invoke(null, onErrorField.get(result))
            param.result = justError
        }
    }

    ::pendragonJsonFetchMessageRequestFingerprint.hookMethod(replaceFetchRequestSingleWithError)
    ::pendragonJsonFetchMessageListRequestFingerprint.hookMethod(replaceFetchRequestSingleWithError)
}
