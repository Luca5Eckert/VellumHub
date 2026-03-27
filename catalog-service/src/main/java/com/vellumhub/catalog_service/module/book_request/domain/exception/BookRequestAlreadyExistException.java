package com.mrs.catalog_service.module.book_request.domain.exception;

public class BookRequestAlreadyExistException extends BookRequestDomainException {

    public BookRequestAlreadyExistException() {
        super("Book request with the same title and author already exists");
    }

}
