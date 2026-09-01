package org.tabooproject.reflex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tabooproject.reflex.asm.AsmSignature
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReflexCacheCompatibilityTest {

    private class Sample {
        fun value(): String = "value"
    }

    @Test
    fun testPublishedCacheGetterDescriptorsRemainCompatible() {
        val reflexGetter = ReflexClass.Companion::class.java.getMethod("getReflexClassCacheMap")
        val signatureGetter = AsmSignature::class.java.getMethod("getCacheMap")

        assertEquals(ConcurrentHashMap::class.java, reflexGetter.returnType)
        assertEquals(ConcurrentHashMap::class.java, signatureGetter.returnType)
    }

    @Test
    fun testConcurrentAsmCreationReturnsSingleWinner() {
        ReflexClass.clearReflexClassCache()
        val classBytes = Sample::class.java.getResourceAsStream("/${Sample::class.java.name.replace('.', '/')}.class")!!.use { it.readBytes() }
        val owner = LazyClass.of(Sample::class.java)
        val threadCount = 8
        val entered = CountDownLatch(threadCount)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)

        try {
            val futures = (0 until threadCount).map {
                executor.submit<ReflexClass> {
                    ReflexClass.of(owner, GateInputStream(classBytes, entered, release), true)
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS), "并发分析未按时进入输入流")
            release.countDown()
            val values = futures.map { it.get(5, TimeUnit.SECONDS) }
            val winner = values.first()
            values.forEach { assertSame(winner, it, "并发调用必须返回缓存中的同一个实例") }
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private class GateInputStream(
        bytes: ByteArray,
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : InputStream() {

        private val delegate = ByteArrayInputStream(bytes)
        private var opened = false

        override fun read(): Int {
            awaitRelease()
            return delegate.read()
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            awaitRelease()
            return delegate.read(bytes, offset, length)
        }

        private fun awaitRelease() {
            if (!opened) {
                opened = true
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
    }
}
