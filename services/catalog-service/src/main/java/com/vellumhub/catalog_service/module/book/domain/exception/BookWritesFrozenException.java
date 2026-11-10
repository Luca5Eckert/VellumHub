package com.vellumhub.catalog_service.module.book.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Operational Book-only cutover fence; a blocked mutation must roll back. */
@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Book writes temporarily frozen")
public class BookWritesFrozenException extends RuntimeException {
    public BookWritesFrozenException() { super("Book writes temporarily frozen"); }
}
