package com.mrs.catalog_service.exception.domain.media;

public class MediaNotExistException extends MediaDomainException {
    public MediaNotExistException() {
        super("Media does not exist");
    }

    public MediaNotExistException(String mediaId) {
        super("Media does not exist with ID: " + mediaId);
    }
}
