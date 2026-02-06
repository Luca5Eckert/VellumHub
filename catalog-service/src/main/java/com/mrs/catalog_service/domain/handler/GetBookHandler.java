package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.domain.model.Book;
import com.mrs.catalog_service.domain.port.BookRepository;
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
