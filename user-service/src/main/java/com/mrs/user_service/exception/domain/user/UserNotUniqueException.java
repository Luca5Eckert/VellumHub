package com.mrs.user_service.exception.domain.user;

public class UserNotUniqueException extends UserDomainException {
    public UserNotUniqueException() {
        super("User is not unique.");
    }
}
