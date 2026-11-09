package com.vellumhub.catalog_service.module.book.domain.port;

/** DB-backed transaction fence for Book-only schema cutover. */
@FunctionalInterface
public interface BookWriteGate {
    void requireWritable();
}
