package com.vellumhub.catalog_service.module.book.domain.handler;

import com.vellumhub.catalog_service.module.book.domain.event.DeleteBookEvent;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotExistException;
import com.vellumhub.catalog_service.module.book.domain.port.BookEventProducer;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DeleteBookHandler {

    private final BookRepository bookRepository;
    private final BookEventProducer<String, DeleteBookEvent> bookEventProducer;
    private final VellumHubMetrics metrics;

    public DeleteBookHandler(BookRepository bookRepository, BookEventProducer<String, DeleteBookEvent> bookEventProducer, VellumHubMetrics metrics) {
        this.bookRepository = bookRepository;
        this.bookEventProducer = bookEventProducer;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(UUID bookId){
        if(!bookRepository.existsById(bookId)) throw new BookNotExistException(bookId.toString());

        bookRepository.deleteById(bookId);

        DeleteBookEvent deleteMediaEvent = new DeleteBookEvent(bookId);

        bookEventProducer.send("deleted-book", bookId.toString(), deleteMediaEvent);
        metrics.recordBusinessCounter(VellumHubMetrics.BOOKS_DELETED, "book_deletion", "success");
    }



}
