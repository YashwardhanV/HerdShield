package dev.herdshield.cache;

public class CacheLoadTimeoutException extends CacheLoadException {
    public CacheLoadTimeoutException(Object key, Throwable cause) {
        super("Cache load timed out for key " + key, cause);
    }
}
