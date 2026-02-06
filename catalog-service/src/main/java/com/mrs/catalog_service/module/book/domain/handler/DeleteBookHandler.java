package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.event.DeleteBookEvent;
import com.mrs.catalog_service.module.book.domain.exception.BookNotExistException;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DeleteBookHandler {

    private final BookRepository bookRepository;
    private final BookEventProducer<String, DeleteBookEvent> bookEventProducer;

    public DeleteBookHandler(BookRepository bookRepository, BookEventProducer<String, DeleteBookEvent> bookEventProducer) {
        this.bookRepository = bookRepository;
        this.bookEventProducer = bookEventProducer;
    }

    @Transactional
    public void execute(UUID bookId){
        if(!bookRepository.existsById(bookId)) throw new BookNotExistException(bookId.toString());

        bookRepository.deleteById(bookId);

        DeleteBookEvent deleteMediaEvent = new DeleteBookEvent(bookId);

        bookEventProducer.send("deleted-book", bookId.toString(), deleteMediaEvent);
    }



}
