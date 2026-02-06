package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.event.DeleteBookEvent;
import com.mrs.catalog_service.domain.exception.BookNotExistException;
import com.mrs.catalog_service.domain.port.EventProducer;
import com.mrs.catalog_service.domain.port.BookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DeleteBookHandler {

    private final BookRepository bookRepository;
    private final EventProducer<String, DeleteBookEvent> eventProducer;

    public DeleteBookHandler(BookRepository bookRepository, EventProducer<String, DeleteBookEvent> eventProducer) {
        this.bookRepository = bookRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public void execute(UUID bookId){
        if(!bookRepository.existsById(bookId)) throw new BookNotExistException(bookId.toString());

        bookRepository.deleteById(bookId);

        DeleteBookEvent deleteMediaEvent = new DeleteBookEvent(bookId);

        eventProducer.send("delete-book", bookId.toString(), deleteMediaEvent);
    }



}
