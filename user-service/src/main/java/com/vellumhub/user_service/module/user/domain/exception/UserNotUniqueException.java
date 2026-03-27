package com.mrs.user_service.module.user.domain.exception;

public class UserNotUniqueException extends UserDomainException {
    public UserNotUniqueException() {
        super("User is not unique.");
    }
}
