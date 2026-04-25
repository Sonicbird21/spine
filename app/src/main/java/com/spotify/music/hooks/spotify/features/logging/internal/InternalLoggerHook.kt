package com.spotify.music.hooks.spotify.features.logging.internal

import com.spotify.music.core.feature.FeatureContext
import com.spotify.music.core.utils.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

class InternalLoggerHook {
    companion object {
        @JvmStatic
        @Volatile
        var isEnabled: Boolean = true

        @JvmStatic
        @Volatile
        var verbosity: InternalLoggerVerbosity = InternalLoggerVerbosity.NORMAL
    }

    private val helperMethodNames = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "core")

    fun install(context: FeatureContext) {
        with(context.baseHook) {
            Logger.info("[InternalLogger] resolving logger fingerprint...")
            val loggerAnchorMember = runCatching { ::internalLoggerMethodFingerprint.member }.getOrElse { err ->
                Logger.error("[InternalLogger] failed to resolve logger fingerprint", err)
                return
            }
            val loggerClass = loggerAnchorMember.declaringClass
            Logger.info("[InternalLogger] resolved logger class: ${loggerClass.name}")
            hookLoggerHelpers(loggerClass)
        }
    }

    private fun hookLoggerHelpers(loggerClass: Class<*>) {
        var totalHooks = 0
        helperMethodNames.forEach { hookMethodName ->
            val hooks = XposedBridge.hookAllMethods(
                loggerClass,
                hookMethodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isEnabled) return
                        val member = param.method
                        val methodName = member.name
                        if (!shouldLogMethod(methodName)) return

                        val level = mapLevel(methodName)
                        val message = buildMessage(
                            methodName,
                            param.args?.map { it }.orEmpty().toTypedArray()
                        )
                        if (message.isBlank()) return

                        val caller = findCaller(member.declaringClass.name)
                        XposedBridge.log("[ProjectSpine][Internal][$level][$caller] $message")
                    }
                }
            )
            totalHooks += hooks.size
        }
        Logger.info("[InternalLogger] installed $totalHooks helper hooks on ${loggerClass.name}")
    }

    private fun shouldLogMethod(methodName: String): Boolean {
        return when (verbosity) {
            InternalLoggerVerbosity.LOW -> methodName == "core"
            InternalLoggerVerbosity.NORMAL -> methodName in setOf("a", "b", "c", "e", "f", "i", "j", "core")
            InternalLoggerVerbosity.HIGH -> methodName != "d"
            InternalLoggerVerbosity.FULL -> true
        }
    }

    private fun mapLevel(methodName: String): String {
        return when (methodName) {
            "a", "g" -> "BREADCRUMB"
            "b", "c", "h" -> "ERROR"
            "e", "f" -> "WARN"
            "i", "j" -> "INFO"
            "core" -> "CORE"
            "d" -> "FMT"
            else -> "LOG"
        }
    }

    private fun buildMessage(methodName: String, args: Array<out Any?>): String {
        return when (methodName) {
            "a", "b", "e", "i", "d" -> formatMessage(
                template = args.getOrNull(0) as? String ?: return "",
                varArgs = extractVarArgs(args.getOrNull(1))
            )
            "c", "f", "j" -> {
                val throwable = args.getOrNull(0) as? Throwable
                val template = args.getOrNull(1) as? String ?: return ""
                val formatted = formatMessage(template, extractVarArgs(args.getOrNull(2)))
                if (throwable != null) "$formatted | ${formatArg(throwable)}" else formatted
            }
            "g" -> formatMessage(
                template = args.getOrNull(1) as? String ?: return "",
                varArgs = extractVarArgs(args.getOrNull(2))
            )
            "h" -> {
                val throwable = args.getOrNull(1) as? Throwable
                val template = args.getOrNull(2) as? String ?: return ""
                val formatted = formatMessage(template, extractVarArgs(args.getOrNull(3)))
                if (throwable != null) "$formatted | ${formatArg(throwable)}" else formatted
            }
            "core" -> args.lastOrNull()?.let(::formatArg) ?: ""
            else -> args.joinToString(", ") { formatArg(it) }
        }
    }

    private fun extractVarArgs(value: Any?): Array<Any?> {
        return when (value) {
            null -> emptyArray()
            is Array<*> -> value.map { it }.toTypedArray()
            else -> arrayOf(value)
        }
    }

    private fun formatMessage(template: String, varArgs: Array<Any?>): String {
        if (varArgs.isEmpty()) return template
        if (template.contains("%s")) {
            return substitutePlaceholders(template, varArgs)
        }
        return runCatching { String.format(template, *varArgs) }
            .getOrElse { "$template ${varArgs.joinToString(prefix = "[", postfix = "]") { formatArg(it) }}" }
    }

    private fun substitutePlaceholders(template: String, varArgs: Array<Any?>): String {
        val parts = template.split("%s")
        if (parts.size == 1) return template

        val out = StringBuilder()
        for (i in parts.indices) {
            out.append(parts[i])
            if (i < parts.lastIndex) {
                val replacement = varArgs.getOrNull(i)?.let(::formatArg) ?: "%s"
                out.append(replacement)
            }
        }
        return out.toString()
    }

    private fun findCaller(loggerClassName: String): String {
        return Thread.currentThread().stackTrace.firstOrNull { element ->
            val className = element.className
            className != loggerClassName &&
                !className.startsWith("com.spotify.music.hooks.spotify.features.logging.internal.") &&
                !className.startsWith("de.robv.android.xposed.")
        }?.let { "${it.className}.${it.methodName}" } ?: "unknown"
    }

    private fun formatArg(value: Any?): String {
        return when (value) {
            null -> "null"
            is Throwable -> "${value::class.java.name}: ${value.message ?: "<no-message>"}"
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { item -> formatArg(item) }
            is IntArray -> value.joinToString(prefix = "[", postfix = "]")
            is LongArray -> value.joinToString(prefix = "[", postfix = "]")
            is FloatArray -> value.joinToString(prefix = "[", postfix = "]")
            is DoubleArray -> value.joinToString(prefix = "[", postfix = "]")
            is BooleanArray -> value.joinToString(prefix = "[", postfix = "]")
            is CharArray -> value.concatToString()
            is ShortArray -> value.joinToString(prefix = "[", postfix = "]")
            is ByteArray -> "ByteArray(size=${value.size})"
            else -> runCatching { value.toString() }.getOrElse { "<toString-failed:${value::class.java.name}>" }
        }
    }
}
