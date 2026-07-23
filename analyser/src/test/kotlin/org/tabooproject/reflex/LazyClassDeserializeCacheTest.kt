package org.tabooproject.reflex

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tabooproject.reflex.serializer.BinaryReader
import org.tabooproject.reflex.serializer.BinaryWriter

/**
 * 验证 LazyClass 反序列化缓存：同一类名在多次反序列化中只创建一个 LazyClass 实例（type 1）
 */
class LazyClassDeserializeCacheTest {

    /**
     * 序列化一个 LazyClass（type 1），多次反序列化后应返回同一个实例
     */
    @Test
    fun testType1DeserializeCacheHit() {
        // 构造一个 type 1 的 LazyClass 字节流
        val original = LazyClass.of("java.lang.String", dimensions = 0, isPrimitive = false) { String::class.java }
        val writer = BinaryWriter()
        original.writeTo(writer)
        val bytes = writer.toByteArray()

        // 多次反序列化
        val first = LazyClass.of(BinaryReader.from(bytes), null)
        val second = LazyClass.of(BinaryReader.from(bytes), null)
        val third = LazyClass.of(BinaryReader.from(bytes), null)

        // 验证是同一个实例
        assertTrue(first === second, "第二次反序列化应命中缓存返回同一实例")
        assertTrue(first === third, "第三次反序列化应命中缓存返回同一实例")
        assertTrue(first.name == "java.lang.String", "类名应为 java.lang.String")
    }

    /**
     * 通过 JavaClassStructure 序列化/反序列化，验证内部所有 type 1 LazyClass 被去重
     */
    @Test
    fun testStructureDeserializeLazyClassDedup() {
        // 解析一个类并序列化
        val structure = ClassAnalyser.analyseByASM(LazyClassDeserializeCacheTest::class.java)
        val writer = BinaryWriter()
        structure.writeTo(writer)
        val bytes = writer.toByteArray()

        // 多次反序列化
        val s1 = JavaClassStructure.of(BinaryReader.from(bytes), null)
        val s2 = JavaClassStructure.of(BinaryReader.from(bytes), null)

        // owner（type 1）应该是同一个实例
        assertTrue(
            s1.owner === s2.owner,
            "多次反序列化后 owner LazyClass 应为同一实例，实际: ${s1.owner} vs ${s2.owner}"
        )

        // superclass（type 1，如果存在）应该是同一个实例
        val sc1 = s1.superclass
        val sc2 = s2.superclass
        if (sc1 != null && sc2 != null) {
            assertTrue(sc1 === sc2, "多次反序列化后 superclass LazyClass 应为同一实例")
        }
    }

    /**
     * 验证不同类名的 LazyClass 不会被错误地共享
     */
    @Test
    fun testDifferentClassesNotShared() {
        val lc1 = LazyClass.of(BinaryReader.from(makeBytes("java.lang.String")), null)
        val lc2 = LazyClass.of(BinaryReader.from(makeBytes("java.lang.Integer")), null)

        assertTrue(lc1 !== lc2, "不同类名的 LazyClass 不应为同一实例")
        assertTrue(lc1.name == "java.lang.String")
        assertTrue(lc2.name == "java.lang.Integer")
    }

    /**
     * 验证 type 2（LazyAnnotatedClass）注解为空时走缓存去重
     */
    @Test
    fun testType2EmptyAnnotationsCacheHit() {
        // 构造一个 type 2 的 LazyAnnotatedClass 字节流（空注解）
        val original = LazyAnnotatedClass(
            "java.util.List", 0, false, false,
            { List::class.java }, emptyList(), "java.util.List", "List"
        )
        val writer = BinaryWriter()
        original.writeTo(writer)
        val bytes = writer.toByteArray()

        // 多次反序列化
        val first = LazyClass.of(BinaryReader.from(bytes), null)
        val second = LazyClass.of(BinaryReader.from(bytes), null)

        assertTrue(first is LazyAnnotatedClass, "应为 LazyAnnotatedClass")
        assertTrue(first === second, "空注解的 type 2 多次反序列化应返回同一实例")
    }

    private fun makeBytes(name: String): ByteArray {
        val writer = BinaryWriter()
        val lc = LazyClass.of(name, dimensions = 0, isPrimitive = false) { Class.forName(name) }
        lc.writeTo(writer)
        return writer.toByteArray()
    }
}
