package com.mrs.catalog_service.module.book_request.domain.exception;

public class BookRequestNotFoundException extends BookRequestDomainException {
    public BookRequestNotFoundException(String message) {
        super(message);
    }

    public BookRequestNotFoundException() {
        super("Book request not found");
    }
}
