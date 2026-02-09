package com.mrs.engagement_service.infrastructure.exception;

public class UserNotAuthenticatedException extends RuntimeException
{
    public UserNotAuthenticatedException(String message) {
        super(message);
    }

}
