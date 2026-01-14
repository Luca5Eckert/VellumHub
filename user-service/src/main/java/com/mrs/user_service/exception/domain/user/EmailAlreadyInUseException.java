package com.mrs.user_service.exception.domain.user;

public class EmailAlreadyInUseException extends UserDomainException {
    public EmailAlreadyInUseException(String email) {
        super("Email already in use: " + email);
    }
}
