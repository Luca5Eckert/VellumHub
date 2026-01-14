package com.mrs.catalog_service.exception.domain.media;

public class InvalidMediaException extends MediaDomainException {
    public InvalidMediaException() {
        super("Media cannot be null");
    }

    public InvalidMediaException(String message) {
        super(message);
    }
}
