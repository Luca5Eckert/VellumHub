package com.vellumhub.user_service.module.auth.domain.exception;

public final class ExternalIdentityProofException extends RuntimeException {
    public ExternalIdentityProofException() {
        super("Additional account verification is required.");
    }
}
