package com.mrs.engagement_service.share.exception;

public class UserNotAuthenticatedException extends RuntimeException
{
    public UserNotAuthenticatedException(String message) {
        super(message);
    }

}
