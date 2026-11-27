package dev.herdshield.cache;

public record CacheEntryView<K>(
        K key,
        String state,
        boolean negative,
        long freshForMs,
        long staleForMs) {
}
