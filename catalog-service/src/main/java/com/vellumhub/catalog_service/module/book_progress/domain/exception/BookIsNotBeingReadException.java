package com.mrs.catalog_service.module.book_progress.domain.exception;

public class BookIsNotBeingReadException extends RuntimeException {

    public BookIsNotBeingReadException() {
        super("Book is not being read");
    }

}
