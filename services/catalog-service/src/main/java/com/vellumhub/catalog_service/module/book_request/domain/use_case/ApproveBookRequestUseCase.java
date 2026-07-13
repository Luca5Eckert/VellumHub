package com.vellumhub.catalog_service.module.book_request.domain.use_case;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.domain.port.BookEventProducer;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book.domain.port.GenreRepository;
import com.vellumhub.catalog_service.module.book_request.domain.BookRequest;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
import com.vellumhub.catalog_service.module.book_request.domain.port.BookRequestRepository;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.CreateBookEvent;
import jakarta.transaction.Transactional;
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

    @Transactional
    public void execute(long requestId) {
        BookRequest bookRequest = bookRequestRepository.findById(requestId)
                .orElseThrow(() -> new BookRequestDomainException("Book request not found"));

        Book book = Book.builder()
                .title(bookRequest.getTitle())
                .author(bookRequest.getAuthor())
                .isbn(bookRequest.getIsbn())
                .description(bookRequest.getDescription())
                .releaseYear(bookRequest.getReleaseYear())
                .coverUrl(bookRequest.getCoverUrl())
                .genres(bookRequest.getGenres())
                .pageCount(bookRequest.getPageCount())
                .publisher(bookRequest.getPublisher())
                .build();

        bookRepository.save(book);

        bookRequestRepository.deleteById(requestId);

        CreateBookEvent createBookEvent = new CreateBookEvent(
                book.getId(),
                book.getTitle(),
                book.getDescription(),
                book.getReleaseYear(),
                book.getCoverUrl(),
                book.getAuthor(),
                bookRequest.getGenres().stream().map(Genre::getName).toList()
        );

        producer.send(KafkaTopics.CREATED_BOOK, book.getId().toString(), createBookEvent);
    }



}
