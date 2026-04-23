@file:Suppress("unused")

package org.sys.config.core.utils

import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge.invokeOriginalMethod
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.findField
import de.robv.android.xposed.XposedHelpers.findFieldIfExists
import de.robv.android.xposed.XposedHelpers.findFirstFieldByExactType
import de.robv.android.xposed.XposedHelpers.getBooleanField
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getLongField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import de.robv.android.xposed.XposedHelpers.newInstance
import de.robv.android.xposed.XposedHelpers.setBooleanField
import de.robv.android.xposed.XposedHelpers.setFloatField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.XposedHelpers.setLongField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.XposedHelpers.setStaticObjectField
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.util.Enumeration

typealias MethodHookParamCompat = MethodHookParam
typealias Replacer = (MethodHookParam) -> Any?

fun MethodHookParam.invokeOriginalMethod(): Any? = invokeOriginalMethod(method, thisObject, args)

inline fun <T, R> T.runCatchingOrNull(func: T.() -> R?) = try {
    func()
} catch (e: Throwable) {
    null
}

fun Any.getObjectField(field: String?): Any? = getObjectField(this, field)

fun Any.getObjectFieldOrNull(field: String?): Any? = runCatchingOrNull {
    getObjectField(this, field)
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.getObjectFieldAs(field: String?) = getObjectField(this, field) as T

@Suppress("UNCHECKED_CAST")
fun <T> Any.getObjectFieldOrNullAs(field: String?) = runCatchingOrNull {
    getObjectField(this, field) as T
}

fun Any.getIntField(field: String?) = getIntField(this, field)

fun Any.getIntFieldOrNull(field: String?) = runCatchingOrNull {
    getIntField(this, field)
}

fun Any.getLongField(field: String?) = getLongField(this, field)

fun Any.getLongFieldOrNull(field: String?) = runCatchingOrNull {
    getLongField(this, field)
}

fun Any.getBooleanFieldOrNull(field: String?) = runCatchingOrNull {
    getBooleanField(this, field)
}

fun Any.callMethod(methodName: String?, vararg args: Any?): Any? =
    callMethod(this, methodName, *args)

fun Any.callMethodOrNull(methodName: String?, vararg args: Any?): Any? = runCatchingOrNull {
    callMethod(this, methodName, *args)
}

fun Class<*>.callStaticMethod(methodName: String?, vararg args: Any?): Any? =
    callStaticMethod(this, methodName, *args)

fun Class<*>.callStaticMethodOrNull(methodName: String?, vararg args: Any?): Any? =
    runCatchingOrNull {
        callStaticMethod(this, methodName, *args)
    }

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.callStaticMethodAs(methodName: String?, vararg args: Any?) =
    callStaticMethod(this, methodName, *args) as T

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.callStaticMethodOrNullAs(methodName: String?, vararg args: Any?) =
    runCatchingOrNull {
        callStaticMethod(this, methodName, *args) as T
    }

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.getStaticObjectFieldAs(field: String?) = getStaticObjectField(this, field) as T

@Suppress("UNCHECKED_CAST")
fun <T> Class<*>.getStaticObjectFieldOrNullAs(field: String?) = runCatchingOrNull {
    getStaticObjectField(this, field) as T
}

fun Class<*>.setStaticObjectField(field: String?, value: Any?) =
    setStaticObjectField(this, field, value)

fun Class<*>.setStaticIntField(field: String?, value: Int) =
    setIntField(getStaticObjectField(this, field), "value", value)

fun Any.setObjectField(field: String?, value: Any?) = setObjectField(this, field, value)
fun Any.setIntField(field: String?, value: Int) = setIntField(this, field, value)
fun Any.setLongField(field: String?, value: Long) = setLongField(this, field, value)
fun Any.setBooleanField(field: String?, value: Boolean) = setBooleanField(this, field, value)
fun Any.setFloatField(field: String?, value: Float) = setFloatField(this, field, value)

fun <T> Class<T>.newInstanceAs(vararg args: Any?): T = newInstance(this, *args) as T

fun Member.findField(name: String?): Field = findField(this.javaClass, name)
fun Member.findFieldIfExists(name: String?): Field? = findFieldIfExists(this.javaClass, name)

inline fun <reified T> Enumeration<*>.asSequence(): Sequence<T> {
    return generateSequence { if (hasMoreElements()) nextElement() as T else null }
}

fun ByteArray.toHexString(): String {
    return joinToString("") { "%02x".format(it) }
}

fun String.toSha256(): String {
    val bytes = this.toByteArray()
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.toHexString()
}
