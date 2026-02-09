package com.mrs.engagement_service.module.book_progress.domain.exception;

public class BookProgressNotFoundException extends BookProgressDomainException {

    public BookProgressNotFoundException() {
        super("Book progress not found");
    }
}
