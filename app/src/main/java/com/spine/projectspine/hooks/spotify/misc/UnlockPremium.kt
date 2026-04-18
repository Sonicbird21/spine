package com.spine.projectspine.hooks.spotify.misc

import com.spine.projectspine.core.hook.BaseHook
import com.spine.projectspine.core.utils.callMethod
import com.spine.projectspine.hooks.spotify.patches.UnlockPremiumPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Constructor
import java.lang.reflect.Field
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
    val isPremiumUpsell = runCatching { ::isPremiumUpsellField.field }.getOrNull()
    XposedBridge.hookAllConstructors(
        contextMenuViewModelClazz,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val isPremiumUpsellField = isPremiumUpsell ?: return
                val parameterTypes = (param.method as Constructor<*>).parameterTypes
                for (i in 0 until param.args.size) {
                    if (parameterTypes[i].name != "java.util.List") continue
                    val original = param.args[i] as? List<*> ?: continue
                    val filtered = original.filter {
                        it?.callMethod("getViewModel")?.let { vm ->
                            isPremiumUpsellField.get(vm)
                        } != true
                    }
                    param.args[i] = filtered
                }
            }
        },
    )

    ::homeStructureGetSectionsFingerprint.hookMethod {
        after { param ->
            val sections = param.result
            runCatching {
                val boolField = sections.javaClass.declaredFields.firstOrNull { it.type == Boolean::class.java }
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
                val boolField = sections.javaClass.declaredFields.firstOrNull { it.type == Boolean::class.java }
                boolField?.let {
                    it.isAccessible = true
                    it.set(sections, true)
                }
            }
            UnlockPremiumPatch.removeBrowseSections(param.result as MutableList<*>)
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
