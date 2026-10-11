package com.vellumhub.catalog_service.module.book.application.handler.create;

import com.vellumhub.catalog_service.module.book.domain.exception.BookDomainException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.domain.port.BookChangeWriter;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.catalog_service.module.book.domain.port.BookExternalProvider;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class CreateBookWithIsbnHandler {

    private final BookRepository bookRepository;
    private final BookExternalProvider bookExternalProvider;

    private final BookChangeWriter bookChangeWriter;


    public CreateBookWithIsbnHandler(BookRepository bookRepository, BookExternalProvider bookExternalProvider, BookChangeWriter bookChangeWriter) {
        this.bookRepository = bookRepository;
        this.bookExternalProvider = bookExternalProvider;
        this.bookChangeWriter = bookChangeWriter;
    }

    @Transactional
    public UUID handle(String isbn){

        if (bookRepository.existsByIsbn(isbn)){
            throw new BookDomainException("A book with the provided ISBN already exists.");
        }

        Book newBook = bookExternalProvider.fetchByIsbn(isbn)
                .orElseThrow(() -> new BookDomainException("Book not found in external provider for ISBN: " + isbn));

        bookRepository.save(newBook);
        bookChangeWriter.record(newBook, BookChangeKind.CREATED);



        return newBook.getId();
    }

}

