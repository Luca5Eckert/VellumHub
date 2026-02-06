package com.mrs.catalog_service.module.book.domain.exception;

public class BookNotExistException extends BookDomainException {
    public BookNotExistException() {
        super("Book does not exist");
    }

    public BookNotExistException(String bookId) {
        super("Book does not exist with ID: " + bookId);
    }
}
