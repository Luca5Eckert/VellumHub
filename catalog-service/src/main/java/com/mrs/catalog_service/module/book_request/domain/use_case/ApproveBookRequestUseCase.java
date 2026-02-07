package com.mrs.catalog_service.module.book_request.domain.use_case;

import com.mrs.catalog_service.module.book.domain.event.CreateBookEvent;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import com.mrs.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.springframework.stereotype.Component;

@Component
public class ApproveBookRequestUseCase {

    private final BookRequestRepository bookRequestRepository;
    private final BookRepository bookRepository;

    private final BookEventProducer<String, CreateBookEvent> producer;

    public ApproveBookRequestUseCase(BookRequestRepository bookRequestRepository, BookRepository bookRepository, BookEventProducer<String, CreateBookEvent> producer) {
        this.bookRequestRepository = bookRequestRepository;
        this.bookRepository = bookRepository;
        this.producer = producer;
    }

    public void execute(long requestId) {
        BookRequest bookRequest = bookRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Book request not found"));

        Book book = Book.builder()
                .title(bookRequest.getTitle())
                .author(bookRequest.getAuthor())
                .isbn(bookRequest.getIsbn())
                .description(bookRequest.getDescription())
                .genres(bookRequest.getGenres())
                .pageCount(bookRequest.getPageCount())
                .publisher(bookRequest.getPublisher())
                .build();

        bookRepository.save(book);

        bookRequestRepository.deleteById(requestId);

        CreateBookEvent createBookEvent = new CreateBookEvent(
                book.getId(),
                book.getGenres()
        );

        producer.send("created-book", book.getId().toString(), createBookEvent);
    }

}
