package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.exception.BookDomainException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookExternalProvider;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class CreateBookWithIsbnHandler {

    private final BookRepository bookRepository;
    private final BookExternalProvider bookExternalProvider;

    public CreateBookWithIsbnHandler(BookRepository bookRepository, BookExternalProvider bookExternalProvider) {
        this.bookRepository = bookRepository;
        this.bookExternalProvider = bookExternalProvider;
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
}