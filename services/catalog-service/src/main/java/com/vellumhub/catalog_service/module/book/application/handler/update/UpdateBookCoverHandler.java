package com.vellumhub.catalog_service.module.book.application.handler.update;

import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book.domain.port.BookChangeWriter;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class UpdateBookCoverHandler {

    private final BookRepository bookRepository;
    private final BookChangeWriter changes;

    public UpdateBookCoverHandler(BookRepository bookRepository, BookChangeWriter changes) {
        this.bookRepository = bookRepository;
        this.changes = changes;
    }

    @Transactional
    public void execute(UUID bookId, String coverUrl) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId.toString()));

        if (Objects.equals(book.getCoverUrl(), coverUrl)) return;
        book.updateCoverUrl(coverUrl);
        bookRepository.save(book);
        changes.record(book, BookChangeKind.UPDATED);
    }
}

