package com.mrs.engagement_service.exception.domain.interaction;

public class InvalidInteractionException extends InteractionDomainException {
    public InvalidInteractionException() {
        super("Interaction cannot be null");
    }

    public InvalidInteractionException(String message) {
        super(message);
    }
}
