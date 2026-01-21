package com.mrs.user_service.module.user.application.exception;

public class UserNotFoundException extends UserApplicationException {
    public UserNotFoundException() {
        super("User not found");
    }
}
