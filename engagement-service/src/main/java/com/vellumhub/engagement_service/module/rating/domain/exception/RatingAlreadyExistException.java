package com.mrs.engagement_service.module.rating.domain.exception;

public class RatingAlreadyExistException extends RatingDomainException {
    public RatingAlreadyExistException(String message) {
        super(message);
    }

    public RatingAlreadyExistException() {
        super("Rating already exist");
    }
}
