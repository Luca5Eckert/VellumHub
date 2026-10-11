package com.vellumhub.catalog_service.module.book.application.handler;

import com.vellumhub.catalog_service.module.book.domain.exception.BookNotExistException;
import com.vellumhub.catalog_service.module.book.domain.port.BookChangeWriter;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DeleteBookHandler {

    private final BookRepository bookRepository;
    private final BookChangeWriter bookChangeWriter;
    private final VellumHubMetrics metrics;

    public DeleteBookHandler(BookRepository bookRepository, BookChangeWriter bookChangeWriter, VellumHubMetrics metrics) {
        this.bookRepository = bookRepository;
        this.bookChangeWriter = bookChangeWriter;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(UUID bookId){
        var book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotExistException(bookId.toString()));
        book.markDeleted(java.time.Instant.now());
        bookRepository.save(book);
        bookChangeWriter.record(book, BookChangeKind.DELETED);
        metrics.recordBusinessCounter(VellumHubMetrics.BOOKS_DELETED, "book_deletion", "success");
    }



}

