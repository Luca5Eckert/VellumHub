package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.application.dto.UpdateBookRequest;
import com.mrs.catalog_service.domain.event.UpdateBookEvent;
import com.mrs.catalog_service.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.domain.model.Book;
import com.mrs.catalog_service.domain.port.EventProducer;
import com.mrs.catalog_service.domain.port.BookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Component
public class UpdateBookHandler {

    private final BookRepository bookRepository;
    private final EventProducer<String, UpdateBookEvent> eventProducer;

    public UpdateBookHandler(BookRepository bookRepository, EventProducer<String, UpdateBookEvent> eventProducer) {
        this.bookRepository = bookRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public void execute(UUID bookId, UpdateBookRequest request) {
        Objects.requireNonNull(request, "UpdateBookRequest must not be null");

        Book book = bookRepository.findById(bookId)
                .orElseThrow(BookNotFoundException::new);

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

        UpdateBookEvent updateMediaEvent = new UpdateBookEvent(
                book.getId(),
                request.genres()
        );

        eventProducer.send("update-book", book.getId().toString(), updateMediaEvent);

    }


}