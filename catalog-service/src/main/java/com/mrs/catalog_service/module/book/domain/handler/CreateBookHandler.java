package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.exception.InvalidBookException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book.domain.event.CreateBookEvent;
import org.springframework.stereotype.Component;

@Component
public class CreateBookHandler {

    private final BookRepository bookRepository;
    private final BookEventProducer<String, CreateBookEvent> bookEventProducer;


    public CreateBookHandler(BookRepository bookRepository, BookEventProducer<String, CreateBookEvent> bookEventProducer) {
        this.bookRepository = bookRepository;
        this.bookEventProducer = bookEventProducer;
    }

    public void handler(Book book){
        if(book == null) throw new InvalidBookException();

        if(bookRepository.existByTitleAndAuthorAndIsbn(
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn()
        )) throw new InvalidBookException("Book with the same title, author and ISBN already exists.");

        bookRepository.save(book);

        CreateBookEvent createBookEvent = new CreateBookEvent(
                book.getId(),
                book.getGenres()
        );

        bookEventProducer.send("created-book", createBookEvent.bookId().toString(), createBookEvent);
    }

}
