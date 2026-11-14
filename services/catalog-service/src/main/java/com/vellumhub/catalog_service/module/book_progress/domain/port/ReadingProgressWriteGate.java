package com.vellumhub.catalog_service.module.book_progress.domain.port;

/** Distributed writer fence scoped to the Reading Progress stream only. */
@FunctionalInterface
public interface ReadingProgressWriteGate {
    void requireWritable();
}
