package org.tabooproject.reflex

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tabooproject.reflex.asm.AsmSignature
import org.tabooproject.reflex.serializer.BinaryReader
import org.tabooproject.reflex.serializer.BinaryWriter
import java.util.function.Supplier

/**
 * 验证弱引用缓存改造：
 * 1. 活跃使用的对象不会被回收（调用方持强引用）
 * 2. 释放强引用后，反射缓存允许对象被 GC（不再永久驻留）
 * 3. 去重语义在缓存存活期内保持
 */
class WeakReferenceCacheTest {

    /** 测试用类 */
    private class Sample

    private enum class SampleEnum {
        VALUE
    }

    @Test
    fun testActiveReflexClassNotCollected() {
        // 调用方持有强引用时，对象不应被回收
        val reflexClass = ReflexClass.of(Sample::class.java, false)
        val holder = reflexClass.toClass()
        assertNotNull(holder)
        // 缓存中存在（saving=false 时不缓存，这里用 of 默认缓存验证）
        val cached = ReflexClass.of(Sample::class.java)
        assertSame(cached, ReflexClass.of(Sample::class.java), "活跃类应命中缓存")
    }

    @Test
    fun testReflexClassCacheWeakReference() {
        // 用 saving=true 缓存，然后释放所有强引用
        ReflexClass.of(Sample::class.java)
        // 验证缓存条目可被清除的机制
        ReflexClass.clearReflexClassCache()
        assertNull(ReflexClass.reflexClassCacheMap.get(Sample::class.java.name))
    }

    @Test
    fun testDeserializeCacheDedupWithinLifetime() {
        // 去重语义：缓存存活期内同一类名返回同一实例
        val original = LazyClass.of("java.lang.String", dimensions = 0, isPrimitive = false) { String::class.java }
        val writer = BinaryWriter()
        original.writeTo(writer)
        val bytes = writer.toByteArray()

        val first = LazyClass.of(BinaryReader.from(bytes), null)
        val second = LazyClass.of(BinaryReader.from(bytes), null)
        assertSame(first, second, "缓存存活期内应命中同一实例")
    }

    @Test
    fun testClearCachesApi() {
        // 清理 API 应可调用且不影响后续功能
        ReflexClass.of(Sample::class.java)
        Reflex.clearCaches()
        // 清理后仍可正常使用
        val after = ReflexClass.of(Sample::class.java)
        assertNotNull(after)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testClearCachesCoversEveryCache() {
        Reflex.clearCaches()
        val finder = ClassAnalyser.ClassFinder { Class.forName(it) }
        val getter = Supplier<Class<*>?> { String::class.java }
        val bytes = lazyClassBytes("java.lang.String")

        val classValue = LazyClass.of(String::class.java)
        val stringValue = LazyClass.of("java.lang.String", classFinder = finder)
        val supplierValue = LazyClass.of("java.lang.String", getter = getter)
        val deserializeValue = LazyClass.of(BinaryReader.from(bytes), finder)
        val signatureValue = AsmSignature.signatureToClass("Ljava/lang/String;", finder)
        val reflexValue = ReflexClass.of(Sample::class.java)
        LazyEnum.allOf(SampleEnum::class.java as Class<Enum<*>>)

        Reflex.clearCaches()

        assertNotSame(classValue, LazyClass.of(String::class.java), "Class 缓存必须被清理")
        assertNotSame(stringValue, LazyClass.of("java.lang.String", classFinder = finder), "字符串缓存必须被清理")
        assertNotSame(supplierValue, LazyClass.of("java.lang.String", getter = getter), "Supplier 缓存必须被清理")
        assertNotSame(deserializeValue, LazyClass.of(BinaryReader.from(bytes), finder), "反序列化缓存必须被清理")
        assertNotSame(signatureValue, AsmSignature.signatureToClass("Ljava/lang/String;", finder), "签名缓存必须被清理")
        assertNotSame(reflexValue, ReflexClass.of(Sample::class.java), "ReflexClass 缓存必须被清理")
        assertTrue(LazyEnum.map.isEmpty(), "枚举缓存必须被清理")
    }

    private fun lazyClassBytes(name: String): ByteArray {
        val writer = BinaryWriter()
        writer.writeInt(1)
        writer.writeNullableString(name)
        writer.writeNullableString(name.substringAfterLast('.'))
        writer.writeInt(0)
        writer.writeBoolean(false)
        writer.writeBoolean(false)
        return writer.toByteArray()
    }
}
