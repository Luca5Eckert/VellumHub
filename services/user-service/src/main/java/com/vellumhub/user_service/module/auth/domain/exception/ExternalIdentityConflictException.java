package com.vellumhub.user_service.module.auth.domain.exception;

public final class ExternalIdentityConflictException extends RuntimeException {
    public ExternalIdentityConflictException() {
        super("This Google identity cannot be associated with the requested account.");
    }
}
