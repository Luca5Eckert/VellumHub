package com.vellumhub.catalog_service.module.book.domain.port;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;

/**
 * Records a durable integration fact while the caller's database transaction is active.
 * No Kafka, serialization or broker dependency leaks into business workflows.
 */
public interface BookChangeWriter {
    void record(Book book, BookChangeKind kind);
}

