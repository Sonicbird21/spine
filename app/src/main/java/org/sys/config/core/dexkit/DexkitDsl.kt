package org.sys.config.core.dexkit

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.base.OpCodesMatcher
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.util.DexSignUtil.getTypeName
import java.lang.reflect.Modifier

private fun getTypeNameCompat(typeName: String): String? {
    return if (typeName.trimStart('[').startsWith('L') && !typeName.endsWith(';')) null else getTypeName(typeName)
}

enum class AccessFlags(val modifier: Int) {
    PUBLIC(Modifier.PUBLIC),
    PRIVATE(Modifier.PRIVATE),
    PROTECTED(Modifier.PROTECTED),
    STATIC(Modifier.STATIC),
    FINAL(Modifier.FINAL),
    CONSTRUCTOR(0)
}

fun MethodMatcher.definingClass(descriptor: String) {
    declaredClass { this.descriptor = descriptor }
}

fun MethodMatcher.strings(vararg strings: String) {
    usingStrings(strings.toList())
}

fun MethodMatcher.parameters(vararg parameters: String) {
    paramTypes(parameters.map(::getTypeNameCompat))
}

fun MethodMatcher.returns(returnType: String) {
    getTypeNameCompat(returnType)?.let { this.returnType = it }
}

fun MethodMatcher.literal(literalSupplier: () -> Number) {
    usingNumbers(literalSupplier())
}

fun MethodMatcher.opcodes(vararg opcodes: Opcode) {
    val opcodeInts = opcodes.map { it.value }
    val matcher = OpCodesMatcher(opcodeInts)
    this.opCodes(matcher)
}

fun MethodMatcher.accessFlags(vararg accessFlags: AccessFlags) {
    val modifiers = accessFlags.fold(0) { result, flag -> result or flag.modifier }
    if (modifiers != 0) modifiers(modifiers)
    if (accessFlags.contains(AccessFlags.CONSTRUCTOR)) {
        name = if (accessFlags.contains(AccessFlags.STATIC)) "<clinit>" else "<init>"
    }
}

class Fingerprint(private val dexkit: DexBridge, init: Fingerprint.() -> Unit) {
    private var classMatcher: ClassMatcher? = null
    private val methodMatcher = MethodMatcher()

    init {
        init(this)
    }

    fun name(name: String) = methodMatcher.name(name)
    fun definingClass(descriptor: String) = classMatcher { this.descriptor = descriptor }
    fun strings(vararg strings: String) = methodMatcher.strings(*strings)
    fun parameters(vararg parameters: String) = methodMatcher.parameters(*parameters)
    fun returns(returnType: String) = methodMatcher.returns(returnType)
    fun literal(literalSupplier: () -> Number) = methodMatcher.literal(literalSupplier)
    fun opcodes(vararg opcodes: Opcode) = methodMatcher.opcodes(*opcodes)
    fun accessFlags(vararg accessFlags: AccessFlags) = methodMatcher.accessFlags(*accessFlags)

    fun methodMatcher(block: MethodMatcher.() -> Unit) {
        methodMatcher.block()
    }

    fun classMatcher(block: ClassMatcher.() -> Unit) {
        classMatcher = ClassMatcher().apply(block)
    }

    fun run(): MethodData {
        var result: MethodData? = null
        dexkit.withBridge { bridge ->
            if (classMatcher != null) {
                result = bridge.findClass {
                    matcher(classMatcher!!)
                }.findMethod {
                    matcher(methodMatcher)
                }.single()
            } else {
                result = bridge.findMethod {
                    matcher(methodMatcher)
                }.single()
            }
        }
        return requireNotNull(result)
    }
}

fun DexBridge.fingerprint(block: Fingerprint.() -> Unit): MethodData {
    return Fingerprint(this, block).run()
}

typealias DexBridge = DexKitCacheBridge.RecyclableBridge
typealias FindClassFunc = DexBridge.() -> ClassData
typealias FindMethodFunc = DexBridge.() -> MethodData
typealias FindMethodListFunc = DexBridge.() -> List<MethodData>
typealias FindFieldFunc = DexBridge.() -> FieldData

fun fingerprint(block: Fingerprint.() -> Unit): FindMethodFunc = { Fingerprint(this, block).run() }

fun findClassDirect(block: FindClassFunc): FindClassFunc = block
fun findMethodDirect(block: FindMethodFunc): FindMethodFunc = block
fun findMethodListDirect(block: FindMethodListFunc): FindMethodListFunc = block
fun findFieldDirect(block: FindFieldFunc): FindFieldFunc = block
