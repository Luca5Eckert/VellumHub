package com.mrs.catalog_service.domain.exception;

public class MediaNotFoundException extends MediaDomainException {
    public MediaNotFoundException() {
        super("Book not found");
    }

    public MediaNotFoundException(String mediaId) {
        super("Book not found with ID: " + mediaId);
    }
}
