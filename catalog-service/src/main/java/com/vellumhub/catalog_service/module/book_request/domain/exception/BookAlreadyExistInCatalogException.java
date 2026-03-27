package com.mrs.catalog_service.module.book_request.domain.exception;

public class BookAlreadyExistInCatalogException extends BookRequestDomainException {

    BookAlreadyExistInCatalogException(String message) {
        super(message);
    }

    public BookAlreadyExistInCatalogException() {
        super("Book with the same title and author already exists in catalog");
    }
}
