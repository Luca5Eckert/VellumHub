package com.mrs.user_service.exception.application;

public class UserNotFoundException extends UserApplicationException {
    public UserNotFoundException() {
        super("User not found");
    }
}
