package com.mrs.user_service.exception.domain;

public class EmailAlreadyInUseException extends UserDomainException {
    public EmailAlreadyInUseException(String email) {
        super("Email already in use: " + email);
    }
}
