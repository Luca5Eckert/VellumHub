package com.mrs.user_service.module.user.domain.exception;

public class EmailAlreadyInUseException extends UserDomainException {
    public EmailAlreadyInUseException(String email) {
        super("Email already in use: " + email);
    }
}
