package com.mrs.catalog_service.module.book.domain.exception;

public class BookNotFoundException extends BookDomainException {
    public BookNotFoundException() {
        super("Book not found");
    }

    public BookNotFoundException(String bookId) {
        super("Book not found with ID: " + bookId);
    }
}
