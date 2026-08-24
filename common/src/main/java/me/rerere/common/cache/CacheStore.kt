/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.common.cache

interface CacheStore<K, V> {
    fun loadEntry(key: K): CacheEntry<V>?
    fun saveEntry(key: K, entry: CacheEntry<V>)
    fun remove(key: K)
    fun clear()
    fun loadAllEntries(): Map<K, CacheEntry<V>>
    fun keys(): Set<K>
}

