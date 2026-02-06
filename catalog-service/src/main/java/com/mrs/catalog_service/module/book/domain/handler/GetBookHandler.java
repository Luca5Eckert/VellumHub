package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetBookHandler {

    private final BookRepository bookRepository;

    public GetBookHandler(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Book execute(UUID bookId){
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId.toString()));
    }

}
