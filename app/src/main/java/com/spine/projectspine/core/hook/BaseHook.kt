package com.spine.projectspine.core.hook

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.spine.projectspine.core.dexkit.FindClassFunc
import com.spine.projectspine.core.dexkit.DexBridge
import com.spine.projectspine.core.dexkit.FindFieldFunc
import com.spine.projectspine.core.dexkit.FindMethodFunc
import com.spine.projectspine.core.dexkit.FindMethodListFunc
import com.spine.projectspine.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import kotlin.reflect.KFunction0
import kotlin.reflect.KProperty0
import kotlin.system.measureTimeMillis

typealias HookFunction = KFunction0<Unit>

abstract class BaseHook(private val app: Application, protected val lpparam: LoadPackageParam) {
    abstract val hooks: Array<HookFunction>
    
    val classLoader: ClassLoader
        get() = lpparam.classLoader

    private val dexkitBridge: DexBridge
        get() = dexkit

    private val cache = app.getSharedPreferences("project_spine_dexkit", Context.MODE_PRIVATE)
    private val applied = mutableSetOf<HookFunction>()
    private val failed = mutableListOf<HookFunction>()

    private val dexkit by lazy {
        System.loadLibrary("dexkit")
        DexKitCacheBridge.init(object : DexKitCacheBridge.Cache {
            override fun clearAll() {
                cache.edit().clear().apply()
            }

            override fun getString(key: String, default: String?): String? = cache.getString(key, default)

            override fun getAllKeys(): Collection<String> = cache.all.keys

            override fun getStringList(key: String, default: List<String>?): List<String>? {
                return cache.getString(key, null)?.takeIf(String::isNotBlank)?.split('|') ?: default
            }

            override fun putString(key: String, value: String) {
                cache.edit().putString(key, value).apply()
            }

            override fun putStringList(key: String, value: List<String>) {
                putString(key, value.joinToString("|"))
            }

            override fun remove(key: String) {
                cache.edit().remove(key).apply()
            }
        })
        DexKitCacheBridge.create("", lpparam.appInfo.sourceDir)
    }

    fun Hook() {
        val elapsed = measureTimeMillis {
            tryLoadCache()
            try {
                applyHooks()
            } finally {
                dexkit.close()
            }
        }
        Logger.info("${lpparam.packageName} handleLoadPackage: ${elapsed}ms")
    }

    private fun tryLoadCache() {
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        val cacheId = "${packageInfo.lastUpdateTime}-${app.packageName}"
        val currentId = cache.getString("cache_id", null)
        if (currentId != cacheId || false) {
            cache.edit().clear().putString("cache_id", cacheId).apply()
        }
    }

    private fun applyHooks() {
        hooks.forEach { hook ->
            if (applied.contains(hook)) return@forEach
            runCatching(hook).onSuccess {
                applied.add(hook)
            }.onFailure { err ->
                failed.add(hook)
                XposedBridge.log(err)
            }
        }
    }

    fun dependsOn(vararg dependentHooks: HookFunction) {
        dependentHooks.forEach { hook ->
            if (applied.contains(hook)) return@forEach
            runCatching(hook).onFailure { err ->
                throw IllegalStateException("Dependent hook ${hook.name} failed", err)
            }.onSuccess {
                applied.add(hook)
            }
        }
    }

    fun KProperty0<FindMethodFunc>.hookMethod(block: HookDsl<HookCallback>.() -> Unit) {
        getDexMethod(this.name, this.get()).toMember().hookMethod(block)
    }

    fun KProperty0<FindMethodFunc>.hookMethod(callback: XC_MethodHook) {
        XposedBridge.hookMethod(getDexMethod(this.name, this.get()).toMember(), callback)
    }

    val KProperty0<FindMethodFunc>.member: Member
        get() = getDexMethod(this.name, this.get()).toMember()

    val KProperty0<FindMethodFunc>.memberOrNull: Member?
        get() = runCatching { this.member }.getOrNull()

    val KProperty0<FindMethodFunc>.method: Method
        get() = getDexMethod(this.name, this.get()).toMethod()

    val KProperty0<FindMethodFunc>.dexMethod: DexMethod
        get() = getDexMethod(this.name, this.get())

    val KProperty0<FindMethodListFunc>.dexMethodList: List<DexMethod>
        get() = getDexMethods(this.name, this.get())

    val KProperty0<FindFieldFunc>.field
        get() = getDexField(this.name, this.get()).toField()

    val KProperty0<FindClassFunc>.clazz
        get() = getDexClass(this.name, this.get()).toClass()

    fun DexClass.toClass(): Class<*> = getInstance(classLoader)

    fun DexMethod.toMethod(): Method {
        var clz = classLoader.loadClass(className)
        do {
            return XposedHelpers.findMethodExactIfExists(clz, name, *paramTypeNames.toTypedArray())
                ?: continue
        } while (clz.superclass.also { clz = it } != null)
        throw NoSuchMethodException("Method $this not found")
    }

    fun DexMethod.toConstructor(): Constructor<*> {
        var clz = classLoader.loadClass(className)
        do {
            return XposedHelpers.findConstructorExactIfExists(clz, *paramTypeNames.toTypedArray())
                ?: continue
        } while (clz.superclass.also { clz = it } != null)
        throw NoSuchMethodException("Constructor $this not found")
    }

    fun DexMethod.toMember(): Member {
        return when {
            isMethod -> toMethod()
            isConstructor -> toConstructor()
            else -> throw NotImplementedError()
        }
    }

    fun DexField.toField() = getFieldInstance(classLoader)

    private inline fun <reified T : Any> wrapFind(
        key: String,
        crossinline findFunc: DexBridge.() -> T,
        crossinline serializer: (T) -> String,
    ): DexBridge.() -> T? {
        return {
            try {
                findFunc().also { Logger.info("$key matches: ${serializer(it)}") }
            } catch (e: Exception) {
                Logger.warn("Fingerprint $key not found")
                null
            }
        }
    }

    private inline fun <reified T : Any> wrapFindList(
        key: String,
        crossinline findFunc: DexBridge.() -> List<T>,
        crossinline serializer: (T) -> String,
    ): DexBridge.() -> List<T> {
        return {
            try {
                findFunc().also { result ->
                    Logger.info("$key matches: ${result.joinToString { serializer(it) }}")
                }
            } catch (e: Exception) {
                Logger.warn("Fingerprint $key not found")
                emptyList()
            }
        }
    }

    private inline fun getDexClass(
        key: String,
        crossinline findFunc: FindClassFunc,
    ): DexClass {
        val classData = wrapFind(key, findFunc) { it.descriptor }.invoke(dexkitBridge)
            ?: throw NoSuchElementException("Dex class for $key not found")
        return DexClass(classData.descriptor)
    }

    private inline fun getDexMethod(
        key: String,
        crossinline findFunc: FindMethodFunc,
    ): DexMethod {
        val methodData = wrapFind(key, findFunc) { it.descriptor }.invoke(dexkitBridge)
            ?: throw NoSuchElementException("Dex method for $key not found")
        return DexMethod(methodData.descriptor)
    }

    private inline fun getDexField(
        key: String,
        crossinline findFunc: FindFieldFunc,
    ): DexField {
        val fieldData = wrapFind(key, findFunc) { it.descriptor }.invoke(dexkitBridge)
            ?: throw NoSuchElementException("Dex field for $key not found")
        return DexField(fieldData.descriptor)
    }

    private inline fun getDexMethods(
        key: String,
        crossinline findFunc: FindMethodListFunc,
    ): List<DexMethod> {
        return wrapFindList(key, findFunc) { it.descriptor }
            .invoke(dexkitBridge)
            .map { DexMethod(it.descriptor) }
    }
}
