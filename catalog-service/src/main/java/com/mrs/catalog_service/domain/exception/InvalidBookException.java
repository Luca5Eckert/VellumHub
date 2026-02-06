package com.mrs.catalog_service.domain.exception;

public class InvalidBookException extends BookDomainException {
    public InvalidBookException() {
        super("Book cannot be null");
    }

    public InvalidBookException(String message) {
        super(message);
    }
}
