package com.mrs.engagement_service.domain.exception;

public class InvalidInteractionException extends InteractionDomainException {
    public InvalidInteractionException() {
        super("Interaction cannot be null");
    }

    public InvalidInteractionException(String message) {
        super(message);
    }
}
