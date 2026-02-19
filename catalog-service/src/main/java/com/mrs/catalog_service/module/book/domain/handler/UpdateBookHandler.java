package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.application.dto.UpdateBookRequest;
import com.mrs.catalog_service.module.book.domain.event.UpdateBookEvent;
import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.exception.InvalidBookException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Component
public class UpdateBookHandler {

    private final BookRepository bookRepository;
    private final BookEventProducer<String, UpdateBookEvent> bookEventProducer;

    public UpdateBookHandler(BookRepository bookRepository, BookEventProducer<String, UpdateBookEvent> bookEventProducer) {
        this.bookRepository = bookRepository;
        this.bookEventProducer = bookEventProducer;
    }

    @Transactional
    public void execute(UUID bookId, UpdateBookRequest request) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(BookNotFoundException::new);

        verifyIfBookAlreadyExists(book, request);

        book.update(
                request.title(),
                request.description(),
                request.coverUrl(),
                request.releaseYear(),
                request.author(),
                request.isbn(),
                request.pageCount(),
                request.publisher(),
                request.genres()
        );

        bookRepository.save(book);

        if(request.genres() == null) return;

        UpdateBookEvent updateBookEvent = new UpdateBookEvent(
                book.getId(),
                request.genres()
        );

        bookEventProducer.send("updated-book", book.getId().toString(), updateBookEvent);

    }

    private void verifyIfBookAlreadyExists(Book book, UpdateBookRequest request) {
        boolean titleChanged = request.title() != null && !request.title().equals(book.getTitle());
        boolean isbnChanged = request.isbn() != null && !request.isbn().equals(book.getIsbn());

        if (!titleChanged && !isbnChanged) return;

        if (bookRepository.existByTitleAndAuthorAndIsbn(request.title(), request.author(), request.isbn())) {
            throw new InvalidBookException("Book with the same title, author and ISBN already exists.");
        }
    }


}