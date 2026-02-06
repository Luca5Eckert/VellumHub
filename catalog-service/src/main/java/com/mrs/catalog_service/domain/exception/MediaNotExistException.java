package com.mrs.catalog_service.domain.exception;

public class MediaNotExistException extends MediaDomainException {
    public MediaNotExistException() {
        super("Book does not exist");
    }

    public MediaNotExistException(String mediaId) {
        super("Book does not exist with ID: " + mediaId);
    }
}
