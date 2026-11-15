package com.vellumhub.catalog_service.module.book.infrastructure.cache;

/** Bounded load shedding: do not turn a slow cache miss into a PostgreSQL stampede. */
public final class CacheLoadOverloadedException extends RuntimeException {
    public CacheLoadOverloadedException(String message) {
        super(message);
    }
}
