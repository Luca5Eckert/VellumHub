package com.vellumhub.catalog_service.module.book_progress.domain.exception;

public class BookProgressConflictException extends BookProgressDomainException {

    public BookProgressConflictException(String message) {
        super(message);
    }
}
