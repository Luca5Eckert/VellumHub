package com.mrs.engagement_service.module.rating.domain.exception;

public class InvalidRatingException extends RatingDomainException {
    public InvalidRatingException() {
        super("Rating cannot be null");
    }

    public InvalidRatingException(String message) {
        super(message);
    }
}
