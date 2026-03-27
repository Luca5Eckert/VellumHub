package com.mrs.user_service.module.user.domain.exception;

public class EmailAlreadyInUseException extends UserDomainException {
    public EmailAlreadyInUseException() {
        super("Email already in use");
    }
}
