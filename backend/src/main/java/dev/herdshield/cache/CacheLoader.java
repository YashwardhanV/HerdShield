package dev.herdshield.cache;

@FunctionalInterface
public interface CacheLoader<K, V> {
    LoadResult<V> load(K key);
}
