package com.vellumhub.catalog_service.module.book_progress.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value=HttpStatus.SERVICE_UNAVAILABLE, reason="Reading Progress writes temporarily frozen")
public class ReadingProgressWritesFrozenException extends RuntimeException {
    public ReadingProgressWritesFrozenException() { super("Reading Progress writes temporarily frozen"); }
}
