package org.tabooproject.reflex

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于 WeakReference 的缓存工具。
 *
 * 与直接使用 `ConcurrentHashMap<K, WeakReference<V>>` 相比，本工具通过引用队列精确清理
 * 已失效的弱引用条目，避免 key 无限累积（ConcurrentHashMap 不会自动清理失效条目）。
 *
 * 语义：
 * - value 只被弱引用持有，无强引用时可被 GC；
 * - 活跃使用中的对象由调用方强引用持有，不会被回收；
 * - 缓存失效后重新计算（去重为 best-effort）。
 *
 * @param threshold 保留该参数以兼容旧调用方，引用队列实现不再依赖阈值
 */
@Suppress("UNUSED_PARAMETER")
class WeakCache<K, V : Any>(
    threshold: Int = 1024,
) {

    private val map = ConcurrentHashMap<K, WeakReference<V>>()
    private val referenceQueue = ReferenceQueue<V>()

    /**
     * 获取缓存值；不存在或已失效返回 null。
     */
    fun get(key: K): V? {
        drainQueue()
        val reference = map[key] ?: return null
        val value = reference.get()
        if (value == null) {
            map.remove(key, reference)
        }
        return value
    }

    /**
     * 获取或计算。返回缓存中的值（若仍存活），否则用 [mapping] 计算并放入缓存。
     * 同一 key 的并发计算由 ConcurrentHashMap 串行化，所有调用返回同一结果。
     */
    fun computeIfAbsent(key: K, mapping: () -> V): V {
        drainQueue()
        var result: V? = null
        map.compute(key) { _, reference ->
            val cached = reference?.get()
            if (cached != null) {
                result = cached
                reference
            } else {
                val value = mapping()
                result = value
                CacheReference(key, value, referenceQueue)
            }
        }
        return result!!
    }

    /**
     * 强制清空缓存。
     */
    fun clear() {
        map.clear()
        while (referenceQueue.poll() != null) {
            // 清空已经入队的引用
        }
    }

    /**
     * 当前条目数（含已失效但未清理的）。
     */
    fun size(): Int {
        drainQueue()
        return map.size
    }

    @Suppress("UNCHECKED_CAST")
    private fun drainQueue() {
        while (true) {
            val reference = referenceQueue.poll() as? CacheReference<K, V> ?: return
            map.remove(reference.key, reference)
        }
    }

    companion object {
        /**
         * 便捷：从 map 中取值（供内部遍历等场景使用）
         */
        @Suppress("unused")
        fun <K, V> getOrNull(map: ConcurrentHashMap<K, WeakReference<V>>, key: K): V? {
            return map[key]?.get()
        }
    }

    private class CacheReference<K, V : Any>(
        val key: K,
        value: V,
        queue: ReferenceQueue<V>,
    ) : WeakReference<V>(value, queue)
}
