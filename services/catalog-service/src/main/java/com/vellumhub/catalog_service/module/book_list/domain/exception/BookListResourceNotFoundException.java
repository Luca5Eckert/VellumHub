package com.vellumhub.catalog_service.module.book_list.domain.exception;

/** Avoid revealing whether a private list or membership exists. */
public class BookListResourceNotFoundException extends RuntimeException {
    public BookListResourceNotFoundException() {
        super("Book list resource not found");
    }
}
