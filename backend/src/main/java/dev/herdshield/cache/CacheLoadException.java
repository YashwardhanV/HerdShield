package dev.herdshield.cache;

public class CacheLoadException extends RuntimeException {
    public CacheLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
