package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.event.CreateBookEvent;
import com.mrs.catalog_service.module.book.domain.exception.BookDomainException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookExternalProvider;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class CreateBookWithIsbnHandler {

    private final BookRepository bookRepository;
    private final BookExternalProvider bookExternalProvider;

    private final BookEventProducer<String, CreateBookEvent> bookEventProducer;


    public CreateBookWithIsbnHandler(BookRepository bookRepository, BookExternalProvider bookExternalProvider, BookEventProducer<String, CreateBookEvent> bookEventProducer) {
        this.bookRepository = bookRepository;
        this.bookExternalProvider = bookExternalProvider;
        this.bookEventProducer = bookEventProducer;
    }

    @Transactional
    public UUID handle(String isbn){

        if (bookRepository.existsByIsbn(isbn)){
            throw new BookDomainException("A book with the provided ISBN already exists.");
        }

        Book newBook = bookExternalProvider.fetchByIsbn(isbn)
                .orElseThrow(() -> new BookDomainException("Book not found in external provider for ISBN: " + isbn));

        bookRepository.save(newBook);



        return newBook.getId();
    }

    /**
     * Publishes a CreateBookEvent to notify other parts of the system about the creation of a new book.
     * @param book The book that was created, used to populate the event data
     */
    private void publishEvent(Book book) {
        CreateBookEvent createBookEvent = new CreateBookEvent(
                book.getId(),
                book.getTitle(),
                book.getDescription(),
                book.getReleaseYear(),
                book.getCoverUrl(),
                book.getAuthor(),
                book.getGenres().stream().map(Genre::getName).toList()
        );

        bookEventProducer.send("created-book", createBookEvent.bookId().toString(), createBookEvent);
    }

}