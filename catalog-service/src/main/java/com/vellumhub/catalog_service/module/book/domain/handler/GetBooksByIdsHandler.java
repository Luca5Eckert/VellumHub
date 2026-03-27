package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetBooksByIdsHandler {

    private final BookRepository bookRepository;

    public GetBooksByIdsHandler(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> execute(List<UUID> uuids) {
        return bookRepository.findAllById(uuids);
    }

}
