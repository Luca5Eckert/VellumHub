package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class UpdateBookCoverHandler {

    private final BookRepository bookRepository;

    public UpdateBookCoverHandler(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional
    public void execute(UUID bookId, String coverUrl) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId.toString()));

        book.update(null, null, coverUrl, null, null, null, null, null, null);

        bookRepository.save(book);
    }
}
