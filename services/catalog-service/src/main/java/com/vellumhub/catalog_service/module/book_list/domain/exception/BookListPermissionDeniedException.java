package com.vellumhub.catalog_service.module.book_list.domain.exception;

/** An authenticated principal is not authorized to modify this list. */
public class BookListPermissionDeniedException extends RuntimeException {
    public BookListPermissionDeniedException() {
        super("Insufficient book list permission");
    }
}
