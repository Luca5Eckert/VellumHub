package com.mrs.catalog_service.domain.exception;

public class MediaNotFoundException extends MediaDomainException {
    public MediaNotFoundException() {
        super("Media not found");
    }

    public MediaNotFoundException(String mediaId) {
        super("Media not found with ID: " + mediaId);
    }
}
