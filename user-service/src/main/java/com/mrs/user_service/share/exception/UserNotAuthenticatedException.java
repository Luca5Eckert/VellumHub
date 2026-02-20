package com.mrs.user_service.share.exception;

import com.mrs.user_service.module.auth.domain.exception.AuthDomainException;

public class UserNotAuthenticatedException extends AuthDomainException {
    public UserNotAuthenticatedException(String message) {
        super(message);
    }
}
