package com.vellumhub.user_service.share.exception;

import com.vellumhub.user_service.module.auth.domain.exception.AuthDomainException;

public class UserNotAuthenticatedException extends AuthDomainException {
    public UserNotAuthenticatedException(String message) {
        super(message);
    }
}
