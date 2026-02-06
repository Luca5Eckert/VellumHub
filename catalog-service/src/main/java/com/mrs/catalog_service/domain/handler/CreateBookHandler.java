package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.exception.InvalidBookException;
import com.mrs.catalog_service.domain.model.Book;
import com.mrs.catalog_service.domain.port.EventProducer;
import com.mrs.catalog_service.domain.port.BookRepository;
import com.mrs.catalog_service.domain.event.CreateBookEvent;
import org.springframework.stereotype.Component;

@Component
public class CreateBookHandler {

    private final BookRepository bookRepository;
    private final EventProducer<String, CreateBookEvent> eventProducer;


    public CreateBookHandler(BookRepository bookRepository, EventProducer<String, CreateBookEvent> eventProducer) {
        this.bookRepository = bookRepository;
        this.eventProducer = eventProducer;
    }

    public void handler(Book book){
        if(book == null) throw new InvalidBookException();

        bookRepository.save(book);

        CreateBookEvent createMediaEvent = new CreateBookEvent(
                book.getId(),
                book.getGenres()
        );

        eventProducer.send("create-book", createMediaEvent.bookId().toString(), createMediaEvent);
    }

}
